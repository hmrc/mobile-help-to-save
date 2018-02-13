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
  // Fine grained feature toggles
  "balanceEnabled": true,
  "paidInThisMonthEnabled": true,
  "firstBonusEnabled": true,
  // URL of page containing information about the Help to Save scheme
  "infoUrl": "https://www.gov.uk/government/publications/help-to-save-what-it-is-and-who-its-for/the-help-to-save-scheme",
  // URL of invitation call to action
  "invitationUrl": "/help-to-save",
  // URL that will redirect enrolled users to the NS&I Help to Save account homepage
  "accessAccountUrl": "/help-to-save/access-account",
  // User-specific Help to Save data
  "user": {
    // users name, optional
    "name": "Beth Planner",
    // user state, can be NotEnrolled, InvitedFirstTime, Invited or Enrolled. See <confluence>/display/NGC/Help+to+Save+User+States
    "state": "Enrolled",
    "account": {
      "balance": 150,
      // Amount user has already paid in this month
      "paidInThisMonth": 40,
      // Headroom left to save, to achieve any further bonus
      "canPayInThisMonth": 10,
      // The amount calculated that the user will receive, two years after they have started the account
      "predictedFirstBonus": 75,
      // Should be constant at £50, but having a property means we are protected from further changes
      "maximumPaidInThisMonth": 50,
      // The date from which the first bonus will be paid, ISO-8601
      "firstBonusFromDate": "2020-01-16T00:00:00+00:00"
    }
  }
}
```

If there is a problem obtaining the user-specific data then the user field will be omitted and the other fields will still be returned (as opposed to an error response being returned).

## License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
