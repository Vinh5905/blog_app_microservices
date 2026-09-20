#!/bin/sh
set -eu

BLOG_URL=${BLOG_URL:-http://localhost:3000}
USERNAME="smoke$(date +%s)"
OTHER_USERNAME="${USERNAME}other"
PASSWORD='SmokeTest123!'
EMAIL="${USERNAME}@example.test"
OTHER_EMAIL="${OTHER_USERNAME}@example.test"

echo "==> Checking frontend"
curl --fail --silent --show-error "${BLOG_URL}/" >/dev/null

echo "==> Signing up ${USERNAME}"
curl --fail --silent --show-error \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"${USERNAME}\",\"email\":\"${EMAIL}\",\"password\":\"${PASSWORD}\"}" \
  "${BLOG_URL}/api/auth/signup" >/dev/null

echo "==> Logging in"
LOGIN_RESPONSE=$(curl --fail --silent --show-error \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"${USERNAME}\",\"password\":\"${PASSWORD}\"}" \
  "${BLOG_URL}/api/auth/login")
TOKEN=$(printf '%s' "${LOGIN_RESPONSE}" | sed -E 's/.*"token":"([^"]+)".*/\1/')
test -n "${TOKEN}"
test "${TOKEN}" != "${LOGIN_RESPONSE}"

echo "==> Signing up and logging in a second user"
curl --fail --silent --show-error \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"${OTHER_USERNAME}\",\"email\":\"${OTHER_EMAIL}\",\"password\":\"${PASSWORD}\"}" \
  "${BLOG_URL}/api/auth/signup" >/dev/null
OTHER_LOGIN_RESPONSE=$(curl --fail --silent --show-error \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"${OTHER_USERNAME}\",\"password\":\"${PASSWORD}\"}" \
  "${BLOG_URL}/api/auth/login")
OTHER_TOKEN=$(printf '%s' "${OTHER_LOGIN_RESPONSE}" | sed -E 's/.*"token":"([^"]+)".*/\1/')
test -n "${OTHER_TOKEN}"
test "${OTHER_TOKEN}" != "${OTHER_LOGIN_RESPONSE}"

echo "==> Creating and reading a post"
POST_RESPONSE=$(curl --fail --silent --show-error \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer ${TOKEN}" \
  -d "{\"title\":\"Smoke test\",\"body\":\"End-to-end verification\"}" \
  "${BLOG_URL}/api/post/createPost")
POST_ID=$(printf '%s' "${POST_RESPONSE}" | sed -E 's/.*"id":([0-9]+).*/\1/')
test -n "${POST_ID}"
curl --fail --silent --show-error \
  -H "Authorization: Bearer ${TOKEN}" \
  "${BLOG_URL}/api/post/getAllPosts" | grep -q 'Smoke test'

echo "==> Rejecting malformed JWT"
MALFORMED_STATUS=$(curl --silent --output /dev/null --write-out '%{http_code}' \
  -H 'Authorization: Bearer not-a-jwt' \
  "${BLOG_URL}/api/post/getAllPosts")
test "${MALFORMED_STATUS}" = '401'

echo "==> Enforcing post ownership"
FORBIDDEN_POST_STATUS=$(curl --silent --output /dev/null --write-out '%{http_code}' \
  -X DELETE \
  -H "Authorization: Bearer ${OTHER_TOKEN}" \
  "${BLOG_URL}/api/post/deletePost/${POST_ID}")
test "${FORBIDDEN_POST_STATUS}" = '403'

echo "==> Creating and reading a comment"
COMMENT_RESPONSE=$(curl --fail --silent --show-error \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer ${TOKEN}" \
  -d "{\"content\":\"Smoke comment\",\"postId\":${POST_ID}}" \
  "${BLOG_URL}/api/comment/addComment")
COMMENT_ID=$(printf '%s' "${COMMENT_RESPONSE}" | sed -E 's/.*"id":([0-9]+).*/\1/')
test -n "${COMMENT_ID}"
curl --fail --silent --show-error \
  -H "Authorization: Bearer ${TOKEN}" \
  "${BLOG_URL}/api/comment/getComments/${POST_ID}" | grep -q 'Smoke comment'

echo "==> Enforcing comment ownership"
FORBIDDEN_COMMENT_STATUS=$(curl --silent --output /dev/null --write-out '%{http_code}' \
  -X DELETE \
  -H "Authorization: Bearer ${OTHER_TOKEN}" \
  "${BLOG_URL}/api/comment/deleteComment/${COMMENT_ID}")
test "${FORBIDDEN_COMMENT_STATUS}" = '403'

echo "==> Cleaning up smoke data"
curl --fail --silent --show-error -X DELETE \
  -H "Authorization: Bearer ${TOKEN}" \
  "${BLOG_URL}/api/comment/deleteComment/${COMMENT_ID}" >/dev/null
curl --fail --silent --show-error -X DELETE \
  -H "Authorization: Bearer ${TOKEN}" \
  "${BLOG_URL}/api/post/deletePost/${POST_ID}" >/dev/null

echo "Smoke test passed: same-origin flow, malformed JWT handling and ownership rules."
