// Domain layer: pure Kotlin (no Android). Business models, repository contracts and use cases.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { jvmToolchain(17) }

dependencies {
    // models carry @Serializable so the API's lenient JSON can be decoded straight into them
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.coroutines.core)
    // JSR-330 @Inject only (no DI framework in the domain layer)
    api(libs.javax.inject)
    // QR code for the UPI payment link (pure Java, no Android dependency)
    api(libs.zxing.core)
    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
