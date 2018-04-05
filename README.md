# mobile-help-to-save

[![Build Status](https://travis-ci.org/hmrc/mobile-help-to-save.svg)](https://travis-ci.org/hmrc/mobile-help-to-save) [ ![Download](https://api.bintray.com/packages/hmrc/releases/mobile-help-to-save/images/download.svg) ](https://bintray.com/hmrc/releases/mobile-help-to-save/_latestVersion)

This microservice contains server-side endpoints that supports mobile app-specific Help to Save functionality.

## API

### GET /mobile-help-to-save/startup

Returns information that the mobile app is likely to need each time it starts.

Response format:

```
{
  "shuttering": {
      "shuttered": false
  },
  // Feature toggle for Help to Save app functionality:
  "enabled": true,
  // Fine grained feature toggles
  "balanceEnabled": true,
  "paidInThisMonthEnabled": true,
  "firstBonusEnabled": true,
  "shareInvitationEnabled": true,
  "savingRemindersEnabled": true,
  // URL of page containing information about the Help to Save scheme
  "infoUrl": "https://www.gov.uk/government/publications/help-to-save-what-it-is-and-who-its-for/the-help-to-save-scheme",
  // URL of invitation call to action
  "invitationUrl": "/mobile-help-to-save",
  // URL that will redirect enrolled users to the NS&I Help to Save account homepage
  "accessAccountUrl": "/mobile-help-to-save/access-account",
  // User-specific Help to Save data
  "user": {
    // users name, optional
    "name": "Beth Planner",
    // user state, can be NotEnrolled, InvitedFirstTime, Invited or Enrolled. See <confluence>/display/NGC/Help+to+Save+User+States
    "state": "Enrolled",
    // Account section is present if:
    // - state is "Enrolled"
    // AND
    // - no errors were encountered whilst fetching the account data from NS&I
    "account": {
      "balance": 150,
      // Amount user has already paid in this month, usually an integer but it's possible to pay in pounds & pence by bank transfer
      "paidInThisMonth": 40.12,
      // Headroom left to save, to achieve any further bonus
      "canPayInThisMonth": 9.88,
      // Should be constant at £50, but having a property means we are protected from further changes
      "maximumPaidInThisMonth": 50,
      "bonusTerms": [
        {
          // The amount calculated that the user will receive, two years after they have started the account
          "bonusEstimate": 75,
          "bonusPaid": 0,
          // The date from which the first bonus will be paid, ISO-8601 date
          "bonusPaidOnOrAfterDate": "2020-01-01"
        }
        // there may optionally be another bonusTerm object here for the second bonus
      ]
    }
  }
}
```

If there is a problem obtaining the user-specific data then the user field will be omitted and the other fields will still be returned (as opposed to an error response being returned).

When the Help to Save section of the app is shuttered then `shuttering.shuttered` will be true and other fields except for feature flags will be omitted:
```
{
  "shuttering": {
    "shuttered": true,
    "title": "Service Unavailable",
    "message": "You’ll be able to use the Help to Save service at 9am on Monday 29 May 2017."
  },
  // Feature toggle for Help to Save app functionality:
  "enabled": true,
  // Fine grained feature toggles
  "balanceEnabled": true,
  "paidInThisMonthEnabled": true,
  "firstBonusEnabled": true,
  "shareInvitationEnabled": true,
  "savingRemindersEnabled": true
}
```

## Testing

To run the tests in this repository:

    sbt test it:test

## License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
