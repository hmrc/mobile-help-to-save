# mobile-help-to-save

[![Build Status](https://travis-ci.org/hmrc/mobile-help-to-save.svg)](https://travis-ci.org/hmrc/mobile-help-to-save) [ ![Download](https://api.bintray.com/packages/hmrc/releases/mobile-help-to-save/images/download.svg) ](https://bintray.com/hmrc/releases/mobile-help-to-save/_latestVersion)

This microservice contains server-side endpoints that supports mobile app-specific Help to Save functionality.

## Development Setup
- Run locally: `sbt run` which runs on port `8248` by default
- Run with test endpoints: `sbt 'run -Dplay.http.router=testOnlyDoNotUseInAppConf.Routes'`

##  Service Manager Profiles
The service can be run locally from Service Manager, using the following profiles:

| Profile Details                  | Command                                                                                                           |
|----------------------------------|:------------------------------------------------------------------------------------------------------------------|
| MOBILE_HELP_TO_SAVE_TESTS          | sm2 --start MOBILE_HELP_TO_SAVE_TESTS --appendArgs '{"MOBILE_HELP_TO_SAVE":["-Dapplication.router=testOnlyDoNotUseInAppConf.Routes"]}'                                                                    |


## Run Tests
- Run Unit Tests:  `sbt test`
- Run Integration Tests: `sbt it:test`
- Run Unit and Integration Tests: `sbt test it:test`
- Run Unit and Integration Tests with coverage report: `sbt clean compile coverage test it:test coverageReport dependencyUpdates`




## API
~~
~~### GET /mobile-help-to-save/startup

Returns information that the mobile app is likely to need each time it starts.

Response format:

```
{
  "shuttering": {
      "shuttered": false,
      "title": "We are shuttered",
      ""message": "Please try again tomorrow"
  },
  // URL of page containing information about the Help to Save scheme
  "infoUrl": "/mobile-help-to-save/info",
  // URL that will redirect enrolled users to the NS&I Help to Save account homepage
  "accessAccountUrl": "/mobile-help-to-save/access-account",
  // User-specific Help to Save data
  "user": {
    // user state, can be "NotEnrolled", "NotEnrolledButEligible", or "Enrolled". See <confluence>/display/NGC/Help+to+Save+User+States
    "state": "Enrolled"
  }
}
```

#### Errors
If there is a problem obtaining the user-specific data then the `user` object will be replaced with a `userError` object. Other fields (feature flags and shuttering) will be unaffected and still returned:
```
  // etc... shuttering and other feature flags omitted for brevity
  "userError": { "code": "GENERAL" }
  // no "user" object
}
```

If there is a problem obtaining the account data then the `user.account` object will be replaced with a `user.accountError` object. Other fields will be unaffected and still returned:
```
  // etc... shuttering and other feature flags omitted for brevity
  "user": {
    "state": "Enrolled"
    "accountError": { "code": "GENERAL" }
    // no "account" object
  }
}
```

If the `user.state` is not `Enrolled` or none of the feature flags that
require account data are `true` then no attempt is made to fetch the account
data and neither `user.account` nor `user.accountError` will be present.

#### Shuttering
When the Help to Save section of the app is shuttered then `shuttering.shuttered` will be true and other fields except for feature flags will be omitted:
```
{
  "shuttering": {
    "shuttered": true,
    "title": "Service Unavailable",
    "message": "You’ll be able to use the Help to Save service at 9am on Monday 29 May 2017."
  }
}
```

## Testing

To run the tests in this repository:

    sbt test it:test

## License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
