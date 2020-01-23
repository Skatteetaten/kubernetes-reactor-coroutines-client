# Kubernetes Reactor Couroutines Client

This is a simple http client for talking with kubernetes. 

It is using `WebClient` from Spring Webflux as the http client and has support for Kotlin coroutines


## Usage

For a complete list of examples look at the `KubernetesClient` integration tests, `KubernetesUserTokenClientIntegrationTest`.

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
        namespace = NAMESPACE
    }
})
``` 

### Create/Post a resource

```kotlin
val s = newSelfSubjectAccessReview {
    spec {
        resourceAttributes {
            namespace = NAMESPACE
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
        namespace = NAMESPACE
        name = NAME
    }
})
```
