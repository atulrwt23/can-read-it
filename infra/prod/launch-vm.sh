#!/usr/bin/env bash
# Creates the beta VM, retrying until Oracle has free Always Free Ampere capacity (docs/deploy.md,
# step 2). Busy single-AD regions such as Mumbai often answer "Out of host capacity" for hours.
#
# Run it in Oracle Cloud Shell (the console's >_ icon), which is already signed in as you:
#   echo 'ssh-ed25519 AAAA… you@example.com' > ~/canreadit.pub      # your PUBLIC key
#   curl -fsSL https://raw.githubusercontent.com/atulrwt23/can-read-it/main/infra/prod/launch-vm.sh | bash
#
# It needs the VCN from the "VCN with Internet Connectivity" wizard, uses the latest Ubuntu 24.04
# image, and creates at most one VM: it stops as soon as an instance with that name exists, so it
# is safe to run again (Cloud Shell closes idle sessions). Override any setting below by env var.
set -uo pipefail

NAME=${NAME:-canreadit}
OCPUS=${OCPUS:-1}
MEMORY_GB=${MEMORY_GB:-6}
BOOT_GB=${BOOT_GB:-100}
VCN_NAME=${VCN_NAME:-canreadit-vcn}
SSH_KEY_FILE=${SSH_KEY_FILE:-$HOME/canreadit.pub}
WAIT_SECONDS=${WAIT_SECONDS:-180}

compartment="${OCI_TENANCY:-}"
[ -n "$compartment" ] || { echo "OCI_TENANCY is not set: run this in Oracle Cloud Shell."; exit 1; }
[ -s "$SSH_KEY_FILE" ] || { echo "Put your SSH public key in $SSH_KEY_FILE first."; exit 1; }
grep -q PRIVATE "$SSH_KEY_FILE" && { echo "$SSH_KEY_FILE holds a PRIVATE key: use the .pub file's content."; exit 1; }

ad=$(oci iam availability-domain list --compartment-id "$compartment" \
  --query 'data[0].name' --raw-output)
vcn=$(oci network vcn list --compartment-id "$compartment" --display-name "$VCN_NAME" \
  --query 'data[0].id' --raw-output)
subnet=$(oci network subnet list --compartment-id "$compartment" --vcn-id "$vcn" \
  --query 'data[?"prohibit-public-ip-on-vnic"==`false`] | [0].id' --raw-output)
image=$(oci compute image list --compartment-id "$compartment" \
  --operating-system "Canonical Ubuntu" --operating-system-version "24.04" \
  --shape VM.Standard.A1.Flex --sort-by TIMECREATED --sort-order DESC \
  --query 'data[0].id' --raw-output)
for v in ad subnet image; do
  if [ -z "${!v}" ] || [ "${!v}" = "null" ]; then
    echo "Could not find the $v: check that $VCN_NAME has a public subnet in your home region."
    exit 1
  fi
done
echo "Availability domain: $ad"
echo "Subnet:              $subnet"
echo "Image:               $image"

attempt=0
while true; do
  state=$(oci compute instance list --compartment-id "$compartment" --display-name "$NAME" \
    --query 'data[?"lifecycle-state"!=`"TERMINATED"`] | [0]."lifecycle-state"' --raw-output 2>/dev/null)
  if [ -n "$state" ] && [ "$state" != "null" ]; then
    echo "The VM '$NAME' exists ($state). Find its public IP under Compute > Instances."
    exit 0
  fi

  attempt=$((attempt + 1))
  echo "$(date -u +%H:%M:%S) UTC: attempt $attempt"
  if out=$(oci compute instance launch \
      --compartment-id "$compartment" \
      --availability-domain "$ad" \
      --display-name "$NAME" \
      --shape VM.Standard.A1.Flex \
      --shape-config "{\"ocpus\": $OCPUS, \"memoryInGBs\": $MEMORY_GB}" \
      --image-id "$image" \
      --boot-volume-size-in-gbs "$BOOT_GB" \
      --subnet-id "$subnet" \
      --assign-public-ip true \
      --ssh-authorized-keys-file "$SSH_KEY_FILE" 2>&1); then
    echo "Created. It takes a minute or two to reach RUNNING; its public IP is under Compute > Instances."
    exit 0
  fi

  if grep -qi "capacity" <<<"$out"; then
    echo "  no capacity yet, trying again in $((WAIT_SECONDS / 60)) minutes"
    sleep "$WAIT_SECONDS"
  elif grep -qi -e "TooManyRequests" -e "429" <<<"$out"; then
    echo "  rate limited, waiting longer"
    sleep $((WAIT_SECONDS * 2))
  else
    echo "$out"
    echo "Stopped because of the unexpected error above."
    exit 1
  fi
done
