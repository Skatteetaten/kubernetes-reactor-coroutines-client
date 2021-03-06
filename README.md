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
val services: List<Service> = kubernetesClient.getMany(newService {
    metadata = newObjectMeta {
        namespace = NAMESPACE
    }
})
``` 

### Get a resource list with label

```kotlin
val projects: List<Project> =
    kubernetesClient.getMany(newProject { metadata { labels = newLabel("removeAfter") } })
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

### Delete a resources
```kotlin
val deleted = kubernetesClient.deleteBackground(newApplicationDeployment {
    metadata {
        name = ""
        namespace = NAMESPACE_DEV
    }
})
```
