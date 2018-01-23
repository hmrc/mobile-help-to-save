# mobile-help-to-save

[![Build Status](https://travis-ci.org/hmrc/mobile-help-to-save.svg)](https://travis-ci.org/hmrc/mobile-help-to-save) [ ![Download](https://api.bintray.com/packages/hmrc/releases/mobile-help-to-save/images/download.svg) ](https://bintray.com/hmrc/releases/mobile-help-to-save/_latestVersion)

This microservice contains server-side endpoints that supports mobile app-specific Help to Save functionality.

## API

### GET /mobile-help-to-save/startup

Returns information that the mobile app is likely to need each time it starts.

Response format:

```
{
  // Feature toggle for Help to Save app functionality:
  "enabled": true,
  // URL of page containing information about the Help to Save scheme
  "infoUrl": "https://www.gov.uk/government/publications/help-to-save-what-it-is-and-who-its-for/the-help-to-save-scheme",
  // URL of invitation call to action
  "invitationUrl": "/help-to-save",
  // URL that will redirect enrolled users to the NS&I Help to Save account homepage
  "accessAccountUrl": "/help-to-save/access-account",
  // User-specific Help to Save data
  "user": {
    // user state, can be NotEnrolled, InvitedFirstTime, Invited or Enrolled. See <confluence>/display/NGC/Help+to+Save+User+States
    "state": "NotEnrolled"
  }
}
```

If there is a problem obtaining the user-specific data then the user field will be omitted and the other fields will still be returned (as opposed to an error response being returned).

## License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
