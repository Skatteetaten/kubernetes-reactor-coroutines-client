# Kubernetes Reactor Couroutines Client

This is a simple http client for talking with kubernetes. 

It is using `WebClient` from Spring Webflux as the http client and has support for Kotlin coroutines


## Usage

For a complete list of examples look at the `KubernetesClient` integration tests, `KubernetesUserTokenClientIntegrationTest`.

Add ` @TargetClient(ClientTypes.SERVICE_ACCOUNT)` to the place where the instance is injected to get the `KubernetesClient` configured with service account token.
Or `@TargetClient(ClientTypes.USER_TOKEN)` to provide a `TokenFetcher` that gets the user token.

### Get a resource 

```kotlin
val dc = kubernetesClient.get(newDeploymentConfig {
    metadata {
        namespace = ""
        name = ""
    }
})
```

### Get a resource list

```kotlin
val pods: PodList = kubernetesClient.getList(newPod {
    metadata {
        namespace = ""
    }
})
``` 

### Create/Post a resource

```kotlin
val s = newSelfSubjectAccessReview {
    spec {
        resourceAttributes {
            namespace = ""
            verb = "update"
            resource = "deploymentconfigs"
        }
    }
}
val selfSubjectAccessView = kubernetesClient.post(s)
```

### Custom resource definitions

```kotlin
val ad: ApplicationDeployment =
kubernetesClient.getResource(newSkatteetatenKubernetesResource<ApplicationDeployment> {
    metadata {
        namespace = ""
        name = ""
    }
})
```
