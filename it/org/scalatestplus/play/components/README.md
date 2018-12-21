# Imported from `scalatestplus-play` v3

The version of `scalatestplus-play` that works with Play 2.5 does not include the traits
and classes that build `Application`s from statically wired components for integration
tests.

The code in this package was lifted directly from `scalatestplus-play` v3, with a minor
tweak to `WithApplicationComponents` to make it compile. Once we migrate to Play 2.6,
which is dependent on a resolution to the problems with later versions of `reactivemongo`,
we can remove this package and use the code directly from `scalatestplus-play`.


