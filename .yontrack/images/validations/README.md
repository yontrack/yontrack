# Validation stamp images

Icons for the validation stamps [`ci.yaml`](../../ci.yaml) declares, one PNG per stamp, named
exactly after the stamp. 128×128, under 16 KB each.

They live here because the CI configuration cannot carry them: `CIBranchConfig.validations` is a
map of stamp name to data-type configuration and has no image field, and this repository holds no
instance-level CasC. Uploading them is therefore a **one-off manual admin step** against the
instance, not something a workflow does on every push — an image is a property of the stamp, not
of a build, and re-uploading it every run would be sixty pointless calls a week.

## Uploading one

Against a **predefined** validation stamp, not a branch-level one: the icon then applies to every
branch where the stamp gets created, which is what `ci.yaml` does on every new branch.

There is no GraphQL mutation for a stamp image — `PredefinedValidationStampMutations` has none —
so this is REST, and **the REST API is not the URL you read the UI at**. On the self-hosted
instance, `https://self.dev.yontrack.com` is the Next.js front end: it proxies `/graphql` and
answers `/rest/...` with its own 404 page. `$YONTRACK_BACKEND_URL` below is the backend.

```bash
# The predefined stamp's id. This one works against either host.
ID=$(curl -s -X POST -H "X-Ontrack-Token: $YONTRACK_TOKEN" -H 'Content-Type: application/json' \
  "$YONTRACK_URL/graphql" \
  -d '{"query":"{ predefinedValidationStamps(name: \"COVERAGE.UNIT\") { id name } }"}' \
  | jq -r '.data.predefinedValidationStamps[0].id')

# The image, as raw base64 in the body
base64 -i COVERAGE.UNIT.png | tr -d '\n' > /tmp/icon.b64
curl -s -X PUT -H "X-Ontrack-Token: $YONTRACK_TOKEN" \
  -H 'Content-Type: text/plain' \
  --data-binary @/tmp/icon.b64 \
  "$YONTRACK_BACKEND_URL/rest/predefinedValidationStamps/$ID/image"
```

`PUT /rest/predefinedValidationStamps/{id}/image` takes the base64 of the PNG as the request body
and decodes it as `image/png` (`PredefinedValidationStampController.putPredefinedValidationStampImage`).
The branch-level equivalent, if you ever want one stamp on one branch to differ, is
`PUT /rest/validationStamps/{id}/image`.

A predefined stamp has to exist before it can be given an image, and `ci.yaml` creates only the
*branch-level* ones. At the time of writing the instance has no `COVERAGE.*` predefined stamp
(`predefinedValidationStamps(name: "COVERAGE")` comes back empty), so each is created once first,
from the UI or with `yontrack validation setup`.

## Regenerating them

Whatever produced an image belongs beside it. Do not hand-edit the PNGs.
