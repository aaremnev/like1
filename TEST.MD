Docker Github Actions Runner
============================

![Deploy - GitHub Actions Runner in Docker](https://github.com/terradatum/github-runner/workflows/GitHub%20Actions%20Runner%20in%20Docker%20-%20Latest/badge.svg)
[![Docker Pulls](https://img.shields.io/docker/pulls/terradatum/github-runner)](https://hub.docker.com/r/terradatum/github-runner)

Originally found at [myoung34/docker-github-actions-runner](https://github.com/myoung34/docker-github-actions-runner) with ideas from [tcardonne/docker-github-runner](https://github.com/tcardonne/docker-github-runner).

## Description ##

This will run the [new self-hosted github actions runners](https://help.github.com/en/actions/automating-your-workflow-with-github-actions/hosting-your-own-runners).

The original project by @myoung34 focuses on a minimal installation of tools. There the guiding principle is to create 
the most basic docker image necessary to run GitHub actions. The advantage to following that principle is that [developers 
can then declare and configure their tools as they see fit](https://github.com/myoung34/docker-github-actions-runner/pull/6#issuecomment-584785114), 
and don't have to "fight" the runner to get things arranged properly.

The drawback to that model is that every workflow has to include the machinery they need to setup and run their build. 
The guiding principle of the GitHub hosted runners follows a different path, more of an "everything, including the 
kitchen sink" approach, where almost any tool and SDK have already been installed and are ready for use out-of-the-box. 
The majority of the installation scripts used by the GitHub Hosted runners are re-purposed here via a submodule pointing 
to GitHub's [actions/virtual-environments](https://github.com/actions/virtual-environments).

In keeping with the [actions/virtual-environments](https://github.com/actions/virtual-environments) setup, the runner's 
home directory is `/home/runner`.

_**Docker image size:**_
- _**Compressed: 19G+.**_
- _**Uncompressed: 50G+.**_

## Docker-in-Docker

This image is designed to run Docker-in-Docker as a non-root user, and therefore expects to use a mount `/var/run/docker.sock` 
as that user. That can be an issue if the `docker` group in the container has a different GID than the group in the host. 
To get around that, the container detects the GID of the bind-mounted socket, and if that GID doesn't exist in the container, 
creates a `dockerhost` group and adds that group to the `runner` user.

Because of the nature of Linux user and group membership, and the fact that the `runner` user doesn't START with the `dockerhost`
group membership, you will see a message like this at startup: "`groups: cannot find name for group ID 969`," where `969` is the
GID of the `docker` group on the Docker host. The container will run just fine, with the correct permissions. However, if
you want to get rid of that message, run the container with the `--group-add=$(stat -c 'g%' /var/run/docker.sock)` command.

❗ **In order to run the container on a host that has SELinux installed and enabled, and the Docker daemon has been started 
without disabling it, you MUST start the container with `--security-opt=label=disable`.**

## Build args ##

The following build args allow you to control the configuration at build time.

| Name | Description | Default value |
|---|---|---|
| `BUILD_DATE` | The build date of the image. Used to set the `org.label-schema.build-date` image label | `$(date -u +'%Y-%m-%dT%H:%M:%SZ')` |
| `VCS_REF` | The git commit hash of the build. Used to set the `org.label-schema.vcs-ref` image label | `${GITHUB_SHA::8}` |
| `TARGETPLATFORM` | The target platform for the build. One of `linux/amd64`, `linux/arm/v7` or `linux/arm64` | `linux/amd64` |
| `UID` | The UID to use for the user | `1000` |
| `GID` | The GID to use for the user's group | `1000` |
| `USER` | The name of the user to run as | `runner` |
| `GROUP` | The name of runner user's group | `runner` |

## Environment variables ##

The following environment variables allow you to control the configuration parameters at runtime.

| Name | Description | Default value |
|---|---|---|
| `RUNNER_REPOSITORY_URL` | The runner will be linked to this repository URL | Required |
| `ACCESS_TOKEN` | Personal Access Token with `repo` access | Required if no `RUNNER_TOKEN` |
| `RUNNER_TOKEN` | Personal Access Token provided by GitHub specifically for running Actions | Required if no `ACCESS_TOKEN` |
| `RUNNER_WORK_DIRECTORY` | Runner's work directory | `/home/runner/work` |
| `RUNNER_NAME` | Name of the runner displayed in the GitHub UI | Hostname of the container | |
| `RUNNER_REPLACE_EXISTING` | `true` will replace existing runner with the same name, `false` will use a random name if there is conflict | `"true"` |

## Runner auto-update behavior ##

The GitHub runner (the binary) will update itself when receiving a job, if a new release is available. In order to allow
the runner to exit and restart by itself, the binary is started by a supervisord process. This also takes care of zombie
reaping since supervisord is running as PID 1.

## Platforms ##

This has been tested and verified on:

 * x86_64

## Examples ##

Manual:

```shell script
docker run -d --restart always \
  --group-add=$(stat -c '%g' /var/run/docker.sock) \
  --security-opt=label=disable \
  -e RUNNER_REPOSITORY_URL="https://github.com/terradatum/repo" \
  -e RUNNER_NAME="foo-runner" \
  -e RUNNER_TOKEN="footoken" \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v github-runner:/home/runner \
  --name=github-runner terradatum/github-runner:latest
```

Or as a shell function (as root):

*Note: the "lite" functions use the self-hosted runner from [myoung34/docker-github-actions-runner](https://github.com/myoung34/docker-github-actions-runner).

```shell script
function github-runner {
    org=$(dirname $1)
    repo=$(basename $1)
    name=github-runner-${repo}
    tag=${3:-latest}
    docker rm -f $name
    docker run -d --restart=always \
        --group-add=$(stat -c '%g' /var/run/docker.sock) \
        --security-opt=label=disable \
        -e RUNNER_REPOSITORY_URL="https://github.com/${org}/${repo}" \
        -e RUNNER_TOKEN="$2" \
        -e RUNNER_NAME="linux-${repo}" \
        -v /var/run/docker.sock:/var/run/docker.sock \
        -v github-runner-${repo}:/home/runner \
        --name=$name ${org}/github-runner:${tag}
}

function github-runner-pat {
    org=$(dirname $1)
    repo=$(basename $1)
    name=github-runner-${repo}
    tag=${3:-latest}
    docker rm -f $name
    docker run -d --restart=always \
        --group-add=$(stat -c '%g' /var/run/docker.sock) \
        --security-opt=label=disable \
        -e ACCESS_TOKEN="$2" \
        -e RUNNER_REPOSITORY_URL="https://github.com/${org}/${repo}" \
        -e RUNNER_NAME="linux-${repo}" \
        -v /var/run/docker.sock:/var/run/docker.sock \
        -v github-runner-${repo}:/home/runner \
        --name=$name ${org}/github-runner:${tag}
}

function github-runner-lite {
    org=$(dirname $1)
    repo=$(basename $1)
    name=github-runner-${repo}
    tag=${3:-latest}
    docker rm -f $name
    docker run -d --restart=always \
        --security-opt=label=disable \
        -e REPO_URL="https://github.com/${org}/${repo}" \
        -e RUNNER_TOKEN="$2" \
        -e RUNNER_NAME="linux-${repo}" \
        -e RUNNER_WORKDIR="/tmp/github-runner-${repo}" \
        -v /var/run/docker.sock:/var/run/docker.sock \
        -v /tmp/github-runner-${repo}:/tmp/github-runner-${repo} \
        --name=$name myoung34/github-runner:${tag}
}

function github-runner-lite-pat {
    org=$(dirname $1)
    repo=$(basename $1)
    name=github-runner-${repo}
    tag=${3:-latest}
    docker rm -f $name
    docker run -d --restart=always \
        --security-opt=label=disable \
        -e ACCESS_TOKEN="$2" \
        -e REPO_URL="https://github.com/${org}/${repo}" \
        -e RUNNER_NAME="linux-${repo}" \
        -e RUNNER_WORKDIR="/tmp/github-runner-${repo}" \
        -v /var/run/docker.sock:/var/run/docker.sock \
        -v /tmp/github-runner-${repo}:/tmp/github-runner-${repo} \
        --name=$name myoung34/github-runner:${tag}
}

github-runner your-account/your-repo       AARGHTHISISYOURGHACTIONSTOKEN
github-runner your-account/some-other-repo ARGHANOTHERGITHUBACTIONSTOKEN ubuntu-xenial
```

Nomad:

```hocon
job "github_runner" {
  datacenters = ["home"]
  type = "system"

  task "runner" {
    driver = "docker"

    env {
      RUNNER_REPOSITORY_URL = "https://github.com/your-account/your-repo"
      RUNNER_TOKEN   = "footoken"
    }

    config {
      privileged = false
      image = "terradatum/github-runner:latest"
      volumes = [
        "/var/run/docker.sock:/var/run/docker.sock",
        "github-runner:/home/runner",
      ]
    }
  }
}
```

Kubernetes:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: actions-runner
  namespace: runners
spec:
  replicas: 1
  selector:
    matchLabels:
      app: actions-runner
  template:
    metadata:
      labels:
        app: actions-runner
    spec:
      volumes:
      - name: dockersock
        hostPath:
          path: /var/run/docker.sock
      - name: runnerhome
        hostPath:
          path: /home/runner
      containers:
      - name: runner
        image: terradatum/github-runner:latest
        env:
        - name: RUNNER_TOKEN
          value: footoken
        - name: RUNNER_REPOSITORY_URL
          value: https://github.com/your-account/your-repo
        - name: RUNNER_NAME
          valueFrom:
            fieldRef:
              fieldPath: metadata.name
        volumeMounts:
        - name: dockersock
          mountPath: /var/run/docker.sock
        - name: runnerhome
          mountPath: /home/runner
```

## Usage From GH Actions Workflow ##

```yaml
name: Package

on:
  release:
    types: [created]

jobs:
  build:
    runs-on: self-hosted
    steps:
    - uses: actions/checkout@v1
    - name: build packages
      run: make all

```

## Automatically Acquiring a Runner Token  ##

A runner token can be automatically acquired at runtime if `ACCESS_TOKEN` (a GitHub personal access token) is a supplied. 
This uses the [GitHub Actions API](https://developer.github.com/v3/actions/self_hosted_runners/#create-a-registration-token). e.g.:

```shell script
docker run -d --restart always --name github-runner \
  --group-add $(stat -c '%g' /var/run/docker.sock) \
  -e ACCESS_TOKEN="footoken" \
  -e RUNNER_REPOSITORY_URL="https://github.com/terradatum/repo" \
  -e RUNNER_NAME="foo-runner" \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v github-runner:/home/runner \
  terradatum/github-runner:latest
```
