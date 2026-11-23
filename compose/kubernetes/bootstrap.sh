#!/bin/bash
# One-shot bootstrap of the k3s cluster of the Kubernetes acceptance stack (#2172), run by the
# `k3s-bootstrap` service of compose/docker-compose-kdsl-kubernetes.yml.
#
# It leaves the cluster ready for Yontrack to start:
#   * the namespace `yontrack`, the service account `yontrack` and the documented RBAC;
#   * the Secret `post-processing-secret`, the one a post-processing is allowed to reference;
#   * the Git server, in the namespace `git`, with its `yontrack` user;
#   * the files of the service account, in the volume Yontrack mounts as if it ran in the cluster;
#   * an admin kubeconfig for the host, to debug with.
#
# Every wait has a deadline and a message: a bootstrap that cannot finish fails, which fails the
# `ComposeUp` that waits for it, instead of hanging. Every step can be run again (a restart of the
# container, a second `up`), and then converges to the same state.
set -euo pipefail

GIT_USER=yontrack
GIT_PASSWORD=yontrack-git-secret
NAMESPACE=yontrack
SECRET_NAME=post-processing-secret
SECRET_VALUE=secret-value-2172

fail() {
  echo "BOOTSTRAP FAILED: $*" >&2
  exit 1
}

# wait_for <seconds> <description> <command...>: runs the command until it succeeds, or fails
wait_for() {
  local timeout=$1 description=$2
  shift 2
  local deadline=$((SECONDS + timeout))
  until "$@" >/dev/null 2>&1; do
    [ "$SECONDS" -lt "$deadline" ] || fail "timeout after ${timeout}s waiting for ${description}"
    sleep 1
  done
}

# 1-2. The admin kubeconfig that k3s writes in the volume it shares with this container, and the API
#    server. The kubeconfig points at 127.0.0.1, which is the k3s container itself: from here the
#    server is `k3s` (a name its certificate has, see --tls-san).
#    The volume outlives the containers, so after a restart of the stack it can still hold the
#    kubeconfig -- and the certificate authority -- of the previous cluster, until k3s writes the new
#    one: the file is read again at every attempt rather than once.
export KUBECONFIG=/tmp/kubeconfig
api_ready() {
  [ -s /k3s-config/k3s.yaml ] || return 1
  sed 's#https://127.0.0.1:6443#https://k3s:6443#' /k3s-config/k3s.yaml > "$KUBECONFIG"
  kubectl get --raw /readyz
}
echo "Waiting for the API server..."
wait_for 180 "the API server (and its kubeconfig)" api_ready

# 3. The node: nothing can be scheduled before it is Ready.
echo "Waiting for the node..."
node_registered() { [ -n "$(kubectl get nodes -o name)" ]; }
wait_for 120 "a node to register" node_registered
kubectl wait --for=condition=Ready node --all --timeout=180s || fail "the node is not Ready"

# 4. The namespace and the service account Yontrack runs as. `create --dry-run | apply` is the
#    idempotent form of `create`.
echo "Creating the namespace and the service account..."
kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -
kubectl -n "$NAMESPACE" create serviceaccount yontrack --dry-run=client -o yaml | kubectl apply -f -

# 5. The Role and the RoleBinding of the documentation.
kubectl apply -f /manifests/rbac.yaml

# 6. The Secret that the post-processing configuration references.
kubectl -n "$NAMESPACE" create secret generic "$SECRET_NAME" \
  --from-literal=token="$SECRET_VALUE" --dry-run=client -o yaml | kubectl apply -f -

# 7. The Git server, and its user. Gitea has no default user: it is created through the CLI of the
#    pod, unless a previous run did.
echo "Deploying the Git server..."
kubectl apply -f /manifests/git.yaml
kubectl -n git rollout status deployment/git --timeout=300s || fail "the Git server does not roll out"
# The list is read whole, then searched: its second column is the user name.
git_users=$(kubectl -n git exec deploy/git -- gitea admin user list 2>/dev/null || true)
if printf '%s\n' "$git_users" | awk -v user="$GIT_USER" '$2 == user { found = 1 } END { exit !found }'; then
  echo "The Git user $GIT_USER exists"
else
  kubectl -n git exec deploy/git -- gitea admin user create --admin \
    --username "$GIT_USER" --password "$GIT_PASSWORD" --email yontrack@example.invalid \
    --must-change-password=false || fail "cannot create the Git user"
fi

# 8. The files of the service account, as Kubernetes mounts them in a pod. The token is a bound one,
#    requested through the API and valid 24 hours; the CA is the one of the cluster. The directory
#    and the files are readable by any user, whichever one the Yontrack image runs as.
echo "Writing the service account files..."
kubectl -n "$NAMESPACE" create token yontrack --duration=24h > /sa/token.tmp || fail "cannot create the token"
kubectl config view --raw --minify -o jsonpath='{.clusters[0].cluster.certificate-authority-data}' \
  | base64 -d > /sa/ca.crt
printf '%s' "$NAMESPACE" > /sa/namespace
mv /sa/token.tmp /sa/token
chmod 755 /sa
chmod 644 /sa/token /sa/ca.crt /sa/namespace
[ -s /sa/token ] && [ -s /sa/ca.crt ] || fail "an empty service account file"

# 9. The admin kubeconfig of the host, for debugging: .yontrack-kdsl/kubeconfig.yaml, with the
#    published port of the API server instead of the name of the container.
kubectl config view --raw --minify \
  | sed "s#server: .*#server: https://localhost:${HOST_API_PORT}#" > /out/kubeconfig.yaml
chmod 644 /out/kubeconfig.yaml

echo "Bootstrap done"
