package io.github.riyadmondol2006.idsheetstudio

/**
 * Marks tests that need things only a developer machine has: the user's remove.bg key saved in
 * the app, or locally built update APKs copied into the app cache. CI excludes them with
 * `-Pandroid.testInstrumentationRunnerArguments.notAnnotation=<this annotation>`.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class RequiresLocalFixtures
