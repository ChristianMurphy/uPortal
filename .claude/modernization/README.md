# Coordinator files for the 6.0 modernization

* `agent-brief.md`: the brief every local agent reads first. The coordinator adds a short task
  brief on top of it.
* `land-push.sh`: checks a landing and pushes it to the integration branch. It refuses a tree
  whose slow jobs didn't pass on a staging branch (`ccr-43ba0785-hzlrj5-landing-<n>`). The landing
  steps are in section 11 of the guide.

The rules themselves are in `docs/developer/modernization/agent-guide.md`. These files are for the
prototype only and don't go upstream.
