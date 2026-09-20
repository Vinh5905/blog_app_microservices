# Kubernetes Labs — BlogApp

Tài liệu ghi lại các lệnh thực hành Kubernetes cơ bản cho nhánh học Kubernetes. Các lệnh dưới đây giả định đang đứng ở thư mục gốc project và `kubectl` đã kết nối tới cluster local (Minikube, Kind hoặc cluster tương đương).

## Lab A — Kiểm tra cluster ban đầu

Kiểm tra context đang dùng:

```bash
kubectl config current-context
```

Kiểm tra các node:

```bash
kubectl get nodes
```

Kết quả mong đợi là ít nhất một node có trạng thái `Ready`.

Có thể xem thêm thông tin node:

```bash
kubectl get nodes -o wide
kubectl describe node <NODE_NAME>
```

## Lab B — Deployment và self-healing

Mục tiêu: tạo một `Deployment` có 3 replicas, xóa thủ công một Pod và quan sát Kubernetes tạo Pod thay thế.

### 1. Manifest Deployment

Tạo file `k8s/lab-b-deployment.yaml` với nội dung:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: lab-b-web
  labels:
    app: lab-b-web
spec:
  replicas: 3
  selector:
    matchLabels:
      app: lab-b-web
  template:
    metadata:
      labels:
        app: lab-b-web
    spec:
      containers:
        - name: nginx
          image: nginx:1.27
          ports:
            - containerPort: 80
```

Điểm cần nhớ:

```text
spec
├── selector
│   └── matchLabels
└── template
    ├── metadata
    │   └── labels
    └── spec
        └── containers
```

`spec.selector` và `spec.template` là hai trường ngang hàng. Trong `template`, `metadata` và `spec` cũng là hai trường ngang hàng.

### 2. Kiểm tra manifest trước khi tạo resource

```bash
kubectl apply --dry-run=client -f k8s/lab-b-deployment.yaml
```

Nếu không có lỗi, áp dụng manifest:

```bash
kubectl apply -f k8s/lab-b-deployment.yaml
```

### 3. Kiểm tra Deployment, ReplicaSet và Pod

```bash
kubectl get deployments
kubectl get replicasets
kubectl get pods -l app=lab-b-web -o wide
kubectl rollout status deployment/lab-b-web
```

Kết quả cần có:

- Deployment `lab-b-web` có `READY 3/3`.
- Có một ReplicaSet do Deployment quản lý.
- Có 3 Pod mang label `app=lab-b-web` ở trạng thái `Running`.

### 4. Xóa một Pod

Lấy tên Pod:

```bash
kubectl get pods -l app=lab-b-web
```

Xóa một Pod bất kỳ:

```bash
kubectl delete pod <POD_NAME>
```

Theo dõi quá trình Kubernetes tạo Pod thay thế:

```bash
kubectl get pods -l app=lab-b-web -w
```

Trạng thái thường thấy của Pod mới là:

```text
Pending → ContainerCreating → Running
```

Kết thúc kiểm tra, số Pod phải quay lại là 3:

```bash
kubectl get pods -l app=lab-b-web
```

### 5. Quan sát cơ chế reconciliation

```bash
kubectl describe deployment lab-b-web
kubectl describe replicaset <REPLICASET_NAME>
kubectl get events --sort-by=.lastTimestamp
```

Chuỗi quản lý là:

```text
Deployment → ReplicaSet → Pods
```

Khi xóa Pod, ReplicaSet phát hiện số lượng thực tế thấp hơn `replicas: 3` và tạo Pod mới để đưa cluster về desired state.

### 6. Dọn Lab B

```bash
kubectl delete -f k8s/lab-b-deployment.yaml
```

Xác nhận resource đã được xóa:

```bash
kubectl get deployment lab-b-web
kubectl get pods -l app=lab-b-web
```

## Các lỗi YAML đã gặp

### Lỗi `spec.selector.template`

Thông báo:

```text
unknown field "spec.selector.template"
```

Nguyên nhân: `template` bị thụt vào bên trong `selector`.

Sai:

```yaml
spec:
  selector:
    template:
```

Đúng:

```yaml
spec:
  selector:
    matchLabels:
      app: lab-b-web
  template:
    metadata:
      labels:
        app: lab-b-web
```

### Lỗi `spec.template.metadata.spec`

Thông báo:

```text
unknown field "spec.template.metadata.spec"
```

Nguyên nhân: `spec` của container bị thụt vào bên trong `template.metadata`.

Sai:

```yaml
template:
  metadata:
    labels:
      app: lab-b-web
    spec:
      containers:
```

Đúng:

```yaml
template:
  metadata:
    labels:
      app: lab-b-web
  spec:
    containers:
```

Khi viết YAML, chỉ dùng spaces, không dùng tab. Nếu apply lỗi, chạy lại:

```bash
kubectl apply --dry-run=client -f k8s/lab-b-deployment.yaml
```

## Lệnh nhanh toàn bộ Lab B

```bash
kubectl config current-context
kubectl get nodes
kubectl apply --dry-run=client -f k8s/lab-b-deployment.yaml
kubectl apply -f k8s/lab-b-deployment.yaml
kubectl rollout status deployment/lab-b-web
kubectl get deployments,replicasets,pods -l app=lab-b-web -o wide
kubectl delete pod <POD_NAME>
kubectl get pods -l app=lab-b-web -w
kubectl describe deployment lab-b-web
kubectl get events --sort-by=.lastTimestamp
kubectl delete -f k8s/lab-b-deployment.yaml
```

## Lab C — Service

Mục tiêu: tạo một `Service` cho Deployment Lab B, truy cập ứng dụng qua địa chỉ ổn định của Service thay vì truy cập trực tiếp Pod IP.

### 1. Kiểm tra Deployment và Pod của Lab B

```bash
kubectl get deployment lab-b-web
kubectl get pods -l app=lab-b-web -o wide
```

Deployment cần có 3 Pod ở trạng thái `Running` trước khi tạo Service.

### 2. Tạo manifest Service

Tạo file `k8s/lab-c-service.yaml`:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: lab-b-web-service
spec:
  type: ClusterIP
  selector:
    app: lab-b-web
  ports:
    - name: http
      port: 80
      targetPort: 80
      protocol: TCP
```

Ý nghĩa các trường port:

```text
client → Service port 80 → Pod targetPort 80
```

`selector.app` phải khớp với label `app: lab-b-web` trong Pod template của Deployment.

Kiểm tra manifest rồi áp dụng:

```bash
kubectl apply --dry-run=client -f k8s/lab-c-service.yaml
kubectl apply -f k8s/lab-c-service.yaml
```

### 3. Kiểm tra Service và endpoints

```bash
kubectl get services
kubectl get service lab-b-web-service
kubectl describe service lab-b-web-service
kubectl get endpoints lab-b-web-service
kubectl get endpointslices
```

`ENDPOINTS` phải chứa Pod IP và port `80`. Nếu thấy `<none>`, kiểm tra selector và labels:

```bash
kubectl get pods --show-labels
kubectl get service lab-b-web-service -o yaml
```

### 4. Truy cập bằng port-forward

Chạy ở terminal thứ nhất và giữ terminal đó mở:

```bash
kubectl port-forward service/lab-b-web-service 8088:80
```

Mở terminal thứ hai rồi gọi Service:

```bash
curl http://127.0.0.1:8088
```

Luồng request là:

```text
localhost:8088 → Service port 80 → một Pod targetPort 80
```

Dừng `port-forward` bằng `Ctrl+C`.

### 5. Gọi Service từ một Pod khác

Tạo một Pod tạm có `curl`, gọi Service qua DNS rồi tự xóa Pod:

```bash
kubectl run curl-client \
  --rm \
  -i \
  --restart=Never \
  --image=curlimages/curl:8.10.1 \
  -- curl -sS http://lab-b-web-service
```

Trong cùng namespace, Service được gọi bằng tên:

```text
http://lab-b-web-service
```

Tên DNS đầy đủ của Service là:

```text
lab-b-web-service.default.svc.cluster.local
```

### 6. Quan sát Service khi Pod bị thay thế

Xem Pod IP và endpoints hiện tại:

```bash
kubectl get pods -l app=lab-b-web -o wide
kubectl get endpoints lab-b-web-service
```

Xóa một Pod:

```bash
kubectl delete pod <POD_NAME>
```

Theo dõi Pod mới và endpoints:

```bash
kubectl get pods -l app=lab-b-web -w
kubectl get endpoints lab-b-web-service -w
```

Pod mới có thể có IP khác, nhưng Service giữ nguyên tên và tự cập nhật danh sách backend.

### 7. Dọn Lab C

```bash
kubectl delete -f k8s/lab-c-service.yaml
```

## Lab G — Persistent Storage

Mục tiêu: gắn một PersistentVolumeClaim (PVC) vào Pod, ghi file vào volume, xóa/thay Pod rồi xác nhận file vẫn còn.

Luồng kiểm tra:

```text
PVC → Pod mount /data → ghi file
xóa/thay Pod → Pod mới mount lại PVC → file vẫn tồn tại
```

### 1. Kiểm tra StorageClass

```bash
kubectl get storageclass
kubectl get sc
```

Cluster cần có StorageClass mặc định để tự tạo PersistentVolume. Với Minikube, nếu chưa có:

```bash
minikube addons enable storage-provisioner
minikube addons enable default-storageclass
```

### 2. Tạo PVC

Tạo file `k8s/lab-g-pvc.yaml`:

```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: lab-g-data
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 1Gi
```

Kiểm tra và áp dụng:

```bash
kubectl apply --dry-run=client -f k8s/lab-g-pvc.yaml
kubectl apply -f k8s/lab-g-pvc.yaml
```

Kiểm tra PVC/PV:

```bash
kubectl get pvc lab-g-data
kubectl get pv
```

PVC cần có trạng thái `Bound`. Nếu vẫn `Pending`:

```bash
kubectl describe pvc lab-g-data
kubectl get storageclass
kubectl get events --sort-by=.lastTimestamp
```

### 3. Tạo Deployment mount PVC

Tạo file `k8s/lab-g-deployment.yaml`:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: lab-g-writer
  labels:
    app: lab-g-writer
spec:
  replicas: 1
  selector:
    matchLabels:
      app: lab-g-writer
  template:
    metadata:
      labels:
        app: lab-g-writer
    spec:
      containers:
        - name: writer
          image: busybox:1.36
          command:
            - /bin/sh
            - -c
          args:
            - |
              echo "Pod $(hostname) đang sử dụng PVC"
              sleep 3600
          volumeMounts:
            - name: persistent-data
              mountPath: /data
      volumes:
        - name: persistent-data
          persistentVolumeClaim:
            claimName: lab-g-data
```

Áp dụng:

```bash
kubectl apply --dry-run=client -f k8s/lab-g-deployment.yaml
kubectl apply -f k8s/lab-g-deployment.yaml
kubectl rollout status deployment/lab-g-writer
kubectl get pods -l app=lab-g-writer
```

### 4. Ghi file vào PVC

Lấy tên Pod:

```bash
kubectl get pods -l app=lab-g-writer
```

Ghi và đọc file:

```bash
kubectl exec <POD_NAME> -- \
  sh -c 'echo "persistent-data-created-by-lab-g" > /data/lab-g.txt'
kubectl exec <POD_NAME> -- cat /data/lab-g.txt
kubectl exec <POD_NAME> -- df -h /data
```

### 5. Xóa Pod để kiểm tra dữ liệu còn tồn tại

Lưu tên Pod hiện tại:

```bash
OLD_POD=$(kubectl get pods \
  -l app=lab-g-writer \
  -o jsonpath='{.items[0].metadata.name}')
echo "$OLD_POD"
```

Xóa Pod và theo dõi Pod mới:

```bash
kubectl delete pod "$OLD_POD"
kubectl get pods -l app=lab-g-writer -w
```

Khi Pod mới ở trạng thái `Running`, đọc lại file:

```bash
NEW_POD=$(kubectl get pods \
  -l app=lab-g-writer \
  -o jsonpath='{.items[0].metadata.name}')
echo "$NEW_POD"
kubectl exec "$NEW_POD" -- cat /data/lab-g.txt
```

Kết quả vẫn phải là:

```text
persistent-data-created-by-lab-g
```

Đây là bằng chứng dữ liệu độc lập với vòng đời của Pod.

### 6. Xác nhận Pod mới mount đúng PVC

```bash
kubectl get pod "$NEW_POD" \
  -o jsonpath='{.spec.volumes[?(@.name=="persistent-data")].persistentVolumeClaim.claimName}{"\n"}'
kubectl get pvc lab-g-data
kubectl get pv
```

Kết quả tên claim phải là `lab-g-data`.

### 7. Lưu ý về access mode và cleanup

Lab dùng `ReadWriteOnce` và chỉ chạy 1 replica. Không scale Deployment này lên nhiều Pod trên nhiều node nếu chưa kiểm tra access mode của storage provider.

Xóa Pod không xóa dữ liệu. Tuy nhiên xóa PVC có thể xóa PV và dữ liệu nếu storage class dùng reclaim policy phù hợp:

```bash
kubectl delete -f k8s/lab-g-deployment.yaml
kubectl delete -f k8s/lab-g-pvc.yaml
```

Chỉ chạy cleanup sau khi đã kiểm tra xong dữ liệu.

## Lệnh nhanh toàn bộ Lab G

```bash
kubectl get storageclass
kubectl apply --dry-run=client -f k8s/lab-g-pvc.yaml
kubectl apply -f k8s/lab-g-pvc.yaml
kubectl get pvc lab-g-data
kubectl apply --dry-run=client -f k8s/lab-g-deployment.yaml
kubectl apply -f k8s/lab-g-deployment.yaml
kubectl rollout status deployment/lab-g-writer
kubectl get pods -l app=lab-g-writer
kubectl exec <POD_NAME> -- sh -c 'echo "persistent-data-created-by-lab-g" > /data/lab-g.txt'
kubectl exec <POD_NAME> -- cat /data/lab-g.txt
kubectl delete pod <POD_NAME>
kubectl get pods -l app=lab-g-writer -w
kubectl exec <NEW_POD_NAME> -- cat /data/lab-g.txt
kubectl delete -f k8s/lab-g-deployment.yaml
kubectl delete -f k8s/lab-g-pvc.yaml
```

## Lab F — ConfigMap và Secret

Mục tiêu: đưa `APP_ENV` vào ConfigMap và một password test vào Secret, sau đó để container đọc chúng qua environment mà không hard-code credential vào image.

### 1. Tạo ConfigMap

```bash
kubectl create configmap lab-f-config \
  --from-literal=APP_ENV=lab-f \
  --dry-run=client -o yaml | kubectl apply -f -
```

Kiểm tra:

```bash
kubectl get configmap lab-f-config
kubectl describe configmap lab-f-config
```

### 2. Tạo Secret

Nhập password test mà không hiển thị trên màn hình:

```bash
read -s TEST_PASSWORD
printf '\\n'
kubectl create secret generic lab-f-secret \
  --from-literal=TEST_PASSWORD="$TEST_PASSWORD" \
  --dry-run=client -o yaml | kubectl apply -f -
unset TEST_PASSWORD
```

Kiểm tra Secret tồn tại mà không in giá trị:

```bash
kubectl get secret lab-f-secret
```

Secret trong Kubernetes mặc định chỉ được encode bằng Base64, không phải mã hóa hoàn chỉnh. Không đưa password thật vào Git hoặc image.

### 3. Tạo Deployment đọc ConfigMap và Secret

Tạo file `k8s/lab-f-config-reader.yaml`:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: lab-f-config-reader
  labels:
    app: lab-f-config-reader
spec:
  replicas: 1
  selector:
    matchLabels:
      app: lab-f-config-reader
  template:
    metadata:
      labels:
        app: lab-f-config-reader
    spec:
      containers:
        - name: reader
          image: busybox:1.36
          command:
            - /bin/sh
            - -c
          args:
            - |
              echo "APP_ENV=${APP_ENV}"
              if [ -n "${TEST_PASSWORD}" ]; then
                echo "TEST_PASSWORD is present"
              else
                echo "TEST_PASSWORD is missing"
                exit 1
              fi
              sleep 3600
          env:
            - name: APP_ENV
              valueFrom:
                configMapKeyRef:
                  name: lab-f-config
                  key: APP_ENV
            - name: TEST_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: lab-f-secret
                  key: TEST_PASSWORD
```

Kiểm tra và áp dụng:

```bash
kubectl apply --dry-run=client -f k8s/lab-f-config-reader.yaml
kubectl apply -f k8s/lab-f-config-reader.yaml
```

### 4. Kiểm tra container đọc được dữ liệu

```bash
kubectl rollout status deployment/lab-f-config-reader
kubectl get pods -l app=lab-f-config-reader
kubectl logs deployment/lab-f-config-reader
```

Log mong đợi:

```text
APP_ENV=lab-f
TEST_PASSWORD is present
```

Kiểm tra trực tiếp trong container:

```bash
kubectl exec deployment/lab-f-config-reader -- printenv APP_ENV
kubectl exec deployment/lab-f-config-reader -- \
  sh -c 'test -n "$TEST_PASSWORD" && echo "TEST_PASSWORD is present"'
```

Không dùng `kubectl logs` hoặc `printenv` để in password thật ra terminal.

### 5. Thay đổi ConfigMap

```bash
kubectl create configmap lab-f-config \
  --from-literal=APP_ENV=staging \
  --dry-run=client -o yaml | kubectl apply -f -
```

Environment lấy từ ConfigMap chỉ được nạp khi Pod khởi động. Restart Deployment:

```bash
kubectl rollout restart deployment/lab-f-config-reader
kubectl rollout status deployment/lab-f-config-reader
kubectl logs deployment/lab-f-config-reader
```

Log mới cần hiển thị:

```text
APP_ENV=staging
```

### 6. Dọn Lab F

```bash
kubectl delete -f k8s/lab-f-config-reader.yaml
kubectl delete configmap lab-f-config
kubectl delete secret lab-f-secret
```

### 7. Luồng dữ liệu của Lab F

```text
ConfigMap ───────┐
                 ├──> Pod environment
Secret ──────────┘

Container image không chứa APP_ENV hoặc password
```

## Lệnh nhanh toàn bộ Lab F

```bash
kubectl create configmap lab-f-config --from-literal=APP_ENV=lab-f --dry-run=client -o yaml | kubectl apply -f -
read -s TEST_PASSWORD; printf '\\n'; kubectl create secret generic lab-f-secret --from-literal=TEST_PASSWORD="$TEST_PASSWORD" --dry-run=client -o yaml | kubectl apply -f -; unset TEST_PASSWORD
kubectl apply --dry-run=client -f k8s/lab-f-config-reader.yaml
kubectl apply -f k8s/lab-f-config-reader.yaml
kubectl rollout status deployment/lab-f-config-reader
kubectl logs deployment/lab-f-config-reader
kubectl rollout restart deployment/lab-f-config-reader
kubectl delete -f k8s/lab-f-config-reader.yaml
kubectl delete configmap lab-f-config
kubectl delete secret lab-f-secret
```

## Lab E — Rollout lỗi và rollback

Mục tiêu: cố tình cập nhật Deployment sang image không tồn tại, đọc lỗi `ImagePullBackOff`, sau đó rollback về image tốt trước đó.

Lab E sử dụng Deployment `lab-b-web` và container tên `nginx` từ Lab B.

### 1. Kiểm tra version đang chạy

```bash
kubectl get deployment lab-b-web
kubectl get pods -l app=lab-b-web
kubectl get deployment lab-b-web \
  -o jsonpath='{.spec.template.spec.containers[0].image}{"\n"}'
kubectl rollout history deployment/lab-b-web
```

Version tốt hiện tại thường là `nginx:1.27`.

### 2. Cố tình deploy image lỗi

Mở terminal thứ nhất để quan sát:

```bash
kubectl get pods -l app=lab-b-web -w
```

Mở terminal thứ hai và đổi image:

```bash
kubectl set image deployment/lab-b-web \
  nginx=nginx:this-tag-does-not-exist
```

Kiểm tra rollout:

```bash
kubectl rollout status deployment/lab-b-web --timeout=60s
```

Lệnh trên có thể timeout hoặc trả exit code khác 0. Đây là kết quả mong đợi vì image không tồn tại.

### 3. Đọc lỗi Pod

```bash
kubectl get pods -l app=lab-b-web -o wide
kubectl get pods -l app=lab-b-web \
  -o custom-columns='NAME:.metadata.name,IMAGE:.spec.containers[0].image,PHASE:.status.phase'
```

Lấy tên Pod đang dùng image lỗi rồi chạy:

```bash
kubectl describe pod <BAD_POD_NAME>
```

Trong phần `Events`, thường thấy:

```text
ErrImagePull
ImagePullBackOff
Failed to pull image
```

Xem các event mới nhất:

```bash
kubectl get events --sort-by=.lastTimestamp
```

Xem ReplicaSet mới và ReplicaSet cũ:

```bash
kubectl get replicasets
```

Không cần tự xóa Pod lỗi hoặc ReplicaSet lỗi; Deployment đang giữ lịch sử để phục vụ rollback.

### 4. Rollback về version trước

Xem lịch sử rollout:

```bash
kubectl rollout history deployment/lab-b-web
```

Rollback về revision ngay trước đó:

```bash
kubectl rollout undo deployment/lab-b-web
```

Theo dõi rollback:

```bash
kubectl rollout status deployment/lab-b-web --timeout=120s
```

Nếu có nhiều revision và muốn chọn cụ thể:

```bash
kubectl rollout undo deployment/lab-b-web --to-revision=<REVISION_NUMBER>
```

### 5. Xác nhận rollback thành công

```bash
kubectl get deployment lab-b-web
kubectl get pods -l app=lab-b-web
kubectl get deployment lab-b-web \
  -o jsonpath='{.spec.template.spec.containers[0].image}{"\n"}'
kubectl rollout history deployment/lab-b-web
```

Kết quả cần có:

- Image quay về `nginx:1.27`.
- Các Pod ở trạng thái `Running` và `Ready`.
- Deployment hoàn tất rollout.

### 6. Kiểm tra Service sau rollback

Nếu Service Lab C vẫn còn:

```bash
kubectl get service lab-b-web-service
kubectl get endpoints lab-b-web-service
```

Có thể kiểm tra HTTP:

```bash
kubectl port-forward service/lab-b-web-service 8088:80
```

Ở terminal khác:

```bash
curl http://127.0.0.1:8088
```

### 7. Luồng hoạt động của Lab E

```text
kubectl set image
        ↓
Deployment tạo ReplicaSet mới
        ↓
Pod mới bị ImagePullBackOff
        ↓
kubectl rollout undo
        ↓
Deployment quay lại ReplicaSet/image trước
```

## Lệnh nhanh toàn bộ Lab E

```bash
kubectl get deployment lab-b-web
kubectl rollout history deployment/lab-b-web
kubectl set image deployment/lab-b-web nginx=nginx:this-tag-does-not-exist
kubectl get pods -l app=lab-b-web -w
kubectl describe pod <BAD_POD_NAME>
kubectl get events --sort-by=.lastTimestamp
kubectl rollout undo deployment/lab-b-web
kubectl rollout status deployment/lab-b-web --timeout=120s
kubectl get deployment lab-b-web
kubectl get pods -l app=lab-b-web
```

## Lab D — Scale Deployment

Mục tiêu: thay đổi số lượng replicas của Deployment theo desired state:

```text
3 replicas → 2 replicas → 5 replicas → 2 replicas
```

Lab D sử dụng Deployment `lab-b-web` đã tạo ở Lab B. Service của Lab C không cần tạo lại.

### 1. Kiểm tra trạng thái ban đầu

```bash
kubectl get deployment lab-b-web
kubectl get pods -l app=lab-b-web
```

### 2. Scale từ 3 xuống 2

Mở terminal thứ nhất để theo dõi Pod:

```bash
kubectl get pods -l app=lab-b-web -w
```

Ở terminal thứ hai:

```bash
kubectl scale deployment lab-b-web --replicas=2
```

Kiểm tra trạng thái:

```bash
kubectl rollout status deployment/lab-b-web
kubectl get deployment lab-b-web
kubectl get pods -l app=lab-b-web
```

Deployment cần trở về `2/2` Pod `Ready`; một Pod sẽ chuyển sang `Terminating`.

### 3. Scale từ 2 lên 5

Ở terminal thứ hai:

```bash
kubectl scale deployment lab-b-web --replicas=5
```

Theo dõi quá trình tạo thêm Pod:

```bash
kubectl rollout status deployment/lab-b-web
kubectl get deployment lab-b-web
kubectl get pods -l app=lab-b-web
```

Kết quả cần đạt là 5 Pod `Running`/`Ready`. Pod mới thường đi qua các trạng thái:

```text
Pending → ContainerCreating → Running
```

### 4. Scale từ 5 về 2

```bash
kubectl scale deployment lab-b-web --replicas=2
kubectl rollout status deployment/lab-b-web
kubectl get deployment lab-b-web
kubectl get pods -l app=lab-b-web
```

Kết quả cuối cùng cần là 2 Pod `Running`/`Ready`.

### 5. Kiểm tra Service sau khi scale

Service vẫn giữ nguyên tên và ClusterIP, chỉ thay đổi số lượng backend endpoint:

```bash
kubectl get service lab-b-web-service
kubectl get endpoints lab-b-web-service
kubectl get endpoints lab-b-web-service -w
```

Khi Deployment có 2 replicas, Service phải có 2 endpoint tương ứng.

### 6. Quan sát ReplicaSet

```bash
kubectl get replicasets
kubectl describe deployment lab-b-web
```

Chuỗi hoạt động:

```text
kubectl scale
    ↓
Deployment.spec.replicas thay đổi
    ↓
ReplicaSet tạo hoặc xóa Pod
    ↓
Service cập nhật endpoints
```

### 7. Lưu ý về file YAML

`kubectl scale` thay đổi resource trực tiếp trên cluster, không sửa file `k8s/lab-b-deployment.yaml`.

Nếu file vẫn chứa:

```yaml
spec:
  replicas: 3
```

thì chạy lại:

```bash
kubectl apply -f k8s/lab-b-deployment.yaml
```

sẽ đưa Deployment về 3 replicas. Đây là ví dụ về sự khác nhau giữa thay đổi trực tiếp trên cluster và cấu hình khai báo trong Git/YAML.

## Lệnh nhanh toàn bộ Lab D

```bash
kubectl get deployment lab-b-web
kubectl get pods -l app=lab-b-web
kubectl scale deployment lab-b-web --replicas=2
kubectl rollout status deployment/lab-b-web
kubectl scale deployment lab-b-web --replicas=5
kubectl rollout status deployment/lab-b-web
kubectl get pods -l app=lab-b-web -w
kubectl scale deployment lab-b-web --replicas=2
kubectl rollout status deployment/lab-b-web
kubectl get deployment lab-b-web
kubectl get pods -l app=lab-b-web
kubectl get endpoints lab-b-web-service
```

Kiểm tra Service đã xóa:

```bash
kubectl get service lab-b-web-service
```

Thông báo `NotFound` sau cleanup là kết quả bình thường.

## Lệnh nhanh toàn bộ Lab C

```bash
kubectl get deployment lab-b-web
kubectl get pods -l app=lab-b-web -o wide
kubectl apply --dry-run=client -f k8s/lab-c-service.yaml
kubectl apply -f k8s/lab-c-service.yaml
kubectl get services
kubectl get endpoints lab-b-web-service
kubectl port-forward service/lab-b-web-service 8088:80
curl http://127.0.0.1:8088
kubectl run curl-client --rm -i --restart=Never --image=curlimages/curl:8.10.1 -- curl -sS http://lab-b-web-service
kubectl delete -f k8s/lab-c-service.yaml
```
