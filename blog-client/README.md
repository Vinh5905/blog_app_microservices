# BlogApp frontend

React 18 with Vite. Use Node 22.12+ (CI and the container builder use Node 22).

```sh
npm ci
npm start
```

The development server listens on port 3000 and proxies `/api` to the gateway on
`localhost:8080`. Container verification uses the repository's `compose.yaml`:
the public entry point remains `http://localhost:3000` and Nginx forwards `/api`
to the gateway. Never put backend credentials in browser environment variables.

```sh
npm test       # Vitest login success/failure tests; fails if no tests
npm run build # production files in build/
```

JSX files use `.jsx`. `index.html` is the Vite entry point; static assets are in
`public/`. The migration from CRA removes its old dependency tree. Frontend tests
and build are required by the release job; the separate frontend Sonar workflow
is still a baseline analysis, not a coverage gate.
