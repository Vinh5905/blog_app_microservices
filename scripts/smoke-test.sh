#!/bin/sh
set -eu

BLOG_URL=${BLOG_URL:-http://localhost:3000}
USERNAME="smoke$(date +%s)"
PASSWORD='SmokeTest123!'
EMAIL="${USERNAME}@example.test"

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

echo "==> Creating and reading a comment"
curl --fail --silent --show-error \
  -H 'Content-Type: application/json' \
  -H "Authorization: Bearer ${TOKEN}" \
  -d "{\"content\":\"Smoke comment\",\"postId\":${POST_ID}}" \
  "${BLOG_URL}/api/comment/addComment" >/dev/null
curl --fail --silent --show-error \
  -H "Authorization: Bearer ${TOKEN}" \
  "${BLOG_URL}/api/comment/getComments/${POST_ID}" | grep -q 'Smoke comment'

echo "Smoke test passed: frontend, auth, posts and comments are reachable through one origin."
