<!-- markdownlint-disable -->

# A stylesheet preference kept the layout's row locks past the monitor (MOD-I406)

Branch `ccr-43ba0785-hzlrj5-stylesheet-prefs-reload` in uPortal, from the integration branch at
`7b24794905`. The uPortal-start branch of the same name points at its integration tip
`39ca485901` and has no commit of its own. It exists so that uPortal-start's runs build this
uPortal branch.

## The hazard

`StylesheetUserPreferencesServiceImpl.setLayoutAttribute` and `removeLayoutAttribute` were
`@Transactional`, and both read the user's layout inside that transaction to get the fragment's
value of the attribute.

1. Request A sets a layout attribute, such as a column width. Its transaction starts.
2. A reads the layout. `DistributedLayoutManager.getDistributedUserLayout` takes the layout
   manager's monitor. If the cached layout was evicted, by a sign-in or by another session's
   change, the read reloads the layout, and `RDBMDistributedLayoutStore` may save it. The save
   locks the user's `UP_USER_PROFILE` rows (MOD-I399) and joins A's transaction. The locks
   stay until A commits. A leaves the monitor.
3. Request B of the same session saves the layout. `saveUserLayout` takes the monitor and then
   waits for the rows that A has locked.
4. When the new value equals the fragment's value, A calls `removeLayoutAttribute`, which reads
   the layout again and needs the monitor, which B has. A waits for B and B waits for A. The
   database sees one waiting transaction and can't break the tie.

For other values A doesn't need the monitor again, and B only waits until A commits.

## The fix

`setLayoutAttribute` and `removeLayoutAttribute` read the layout before the transaction starts,
as MOD-D56 item 7 decided. They are no longer `@Transactional`. Each gets the fragment's
preferences from the layout first. It then runs its old body through the portal's
`TransactionOperations` and passes those preferences in, so that the code inside the
transaction never reads the layout. A reload now runs in the store's own transactions, which
end before the layout manager leaves its monitor.

`getLayoutAttribute` and `populateLayoutAttributes` remove a value that equals the default.
They called `this.removeLayoutAttribute` for that, which runs without a transaction. They now
call the body directly, still without a transaction, with the fragment's preferences they
already have.

`UpdatePreferencesServlet` is the only caller of the two methods, and it has no transaction of
its own.

The service's other three `@Transactional` methods (`removeOutputProperty`,
`setStylesheetParameter` and `removeStylesheetParameter`) are unchanged. They don't read the
layout through the session's layout manager. They ask for the user instance, and on a
session's first request that builds a layout manager and loads the layout. A second request
can't be inside that new manager's monitor. The worst case there is a wait until the commit.

## Evidence

`RDBMUserLayoutStoreConcurrencyTest.testLayoutSavedWhileAStylesheetPreferenceIsSet` (JDK 21,
HSQLDB with MVCC) runs the real service behind the portal's transaction advice, a real
`DistributedLayoutManager` and the test's layout store. A sets a preference, its layout read
reloads and saves, and A stops before it stores the preference. B then saves the layout through
the same layout manager and gets five seconds.

On the tip, with only the line removed that hands the service its `TransactionOperations` (the
tip has no such setter):

```
RDBMUserLayoutStoreConcurrencyTest > testLayoutSavedWhileAStylesheetPreferenceIsSet FAILED
java.lang.AssertionError: B's save waited for the transaction of A's preference
```

B was still waiting after five seconds and finished once A committed. The test stops A at a
point after which it doesn't need the monitor again, so that the old code fails the test
instead of hanging it. With the fix the test passes, with the class's other seven tests.

`StylesheetUserPreferencesServiceImplTest.testLayoutAttributeWritesReadTheLayoutOutsideTheirTransaction`
covers step 4 without threads. It sets the user's own value, the default and the fragment's
value, removes the attribute, and counts the layout reads made inside the transaction. With a
seeded fault (the layout read moved into the transaction of `removeLayoutAttribute`) it failed,
and the fault isn't committed.

`:uPortal-layout:uPortal-layout-impl:test`, `:uPortal-api:uPortal-api-rest:test`,
`spotlessCheck` and `:uPortal-layout:uPortal-layout-impl:spotbugsMain` pass on JDK 21. The
tests of the two changed classes pass on JDK 25.

## Runs

The push runs of both branches are added here when they finish.

## Proposed LOG rows

| ID | Area | Finding | Status |
| --- | --- | --- | --- |
| MOD-I406 (update) | Layout | A layout reload inside `StylesheetUserPreferencesServiceImpl.setLayoutAttribute`'s transaction kept the user's layout row locks after the layout manager's monitor was released. | **Fixed** (uPortal `fix(layout): read the layout before a stylesheet preference transaction`, MOD-D56 item 7): `setLayoutAttribute` and `removeLayoutAttribute` read the layout first and then run in a transaction that doesn't read it. `RDBMUserLayoutStoreConcurrencyTest` fails on the old code, where a save of the session's layout has to wait for the transaction of a preference whose layout read reloaded and saved. A unit test counts the layout reads inside the transaction (`notes/stylesheet-prefs-reload.md`). |
