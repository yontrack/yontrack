Once a build has reached the BRONZE promotion, it's not ready to be deployed because no image is available anywhere.

From a high level, I want the possibility to deploy any successful build (BRONZE) to a demo environment before I can
actually perform a public release.

A public release is a build where the documentation has been published and where the image is available in the Docker
Hub registry.

Publishing to a demo environment would be done nightly, using the latest BRONZE build, or on-demand. In this case, it's
possible to specify the display name of the build to be deployed. If not specified, the latest BRONZE build will be
used.

This triggers the deployment on a demo environment and some basic smoke tests are run. If the deployment is successful,
the build is promoted to the SILVER promotion. This deployment would need the image (s) to be available – maybe the
private registry in Digital Ocean can be used for this purpose.

Once SILVER promotion is complete, a notification is sent to Slack.

The demo environment can be quickly tested overseen by a human operator. When it's OK, the build is promoted to GOLD.

The GOLD promotion triggers the actual public release:

* documentation
* image in Docker Hub
* publication of the release notes in GitHub together with the release
* publication of release notes in Slack
* creation of the release notes in the Wiki

As much as possible, the demo publication & the public release creation must not rebuild anything. Once publicly
released, the build display name in Yontrack must be updated to the final release name.

There is already a demo environment available in the yontrack-infra-gitops repository for Yontrack (yontrack-demo). It
must be aligned as much a possible to the production environment (yontrack-self) from an infrastructure perspective.

Some demo data must be made available in the demo environment, possibly reflecting the changes since the latest
deployment.

