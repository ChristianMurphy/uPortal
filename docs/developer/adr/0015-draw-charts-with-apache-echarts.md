---
status: proposed
date: 2026-10-10
decision-makers: Christian Murphy
consulted: none yet
informed: none yet

---

# Draw statistics charts with Apache ECharts

## Context and Problem Statement

uPortal draws its statistics charts with Google Charts and builds the chart data on the server
with `google-visualization-java`. The Statistics, Activity, Popular Portlets and SQL Query portlets
use them, along with the CSV and HTML table views, `reportGraph.jsp`, `reportList.jsp` and the SQL
Query chart JSPs.

* The `google-visualization-java` version uPortal uses is a fork published only on jitpack.io,
  which is a supply-chain and availability risk. The fork is unmaintained. Google's original, 1.1.1
  from 2013 on Maven Central, writes invalid JSON for null cells.
* Google Charts is closed source, and its terms don't allow hosting it, so every viewer's browser
  loads it from Google. That conflicts with a strict Content Security Policy and with student
  privacy reviews.

Which chart library should uPortal use, and how does it get there?

## Decision Drivers

* An open-source licence, with the library served by the portal itself.
* An active project.
* Accessible charts, with descriptions for screen readers and patterns for colour-blind users.
* No build dependency outside Maven Central.
* A download size that suits a few charts in a portal page.

## Considered Options

* Apache ECharts
* Vega-Lite
* Plotly.js
* Chart.js
* Observable Plot, uPlot, D3, Highcharts, ApexCharts or IBM Carbon Charts

## Decision Outcome

Chosen option: "Apache ECharts", because it is an Apache Software Foundation project under the
Apache-2.0 licence with recent releases, it has the accessibility features listed above, and its
chart types can be imported one at a time.

The change comes in two steps:

1. uPortal gets its own small table model in place of `google-visualization-java`. Jackson writes
   its JSON and plain RFC 4180 escaping writes its CSV. The JSON is the same as what Google Charts
   reads today, so the pages don't change, and the null-cell bug is fixed. jitpack.io leaves the
   build.
2. With the portlet replacement (ADR 0012), REST endpoints return plain JSON rows, and web
   components draw them with ECharts imported from `echarts/core`. Google Charts then leaves the
   pages.

Both steps are planned, and the first one is being built on a separate branch.

### Consequences

* Good, because the portal serves its own chart code, which works with a strict Content Security
  Policy and keeps viewers' browsers from contacting Google.
* Good, because the build no longer depends on jitpack.io.
* Bad, because the existing charts are rewritten for ECharts in step 2.
* Bad, because uPortal maintains its own small table model in step 1.

### Confirmation

Step 1 needs unit tests that compare the new JSON and CSV output with the old output, including
null cells. After step 2, the chart components get Vitest browser-mode tests (ADR 0013), and a search of
the pages for `google.visualization` and of the build for `jitpack` finds nothing.

## Pros and Cons of the Options

### Apache ECharts

Apache-2.0. Version 6.0 came out in July 2025 and 6.1 in May 2026.

* Good, because it draws SVG or canvas and has screen reader descriptions, decal patterns for
  colour-blind users and dark mode.
* Good, because chart types import one at a time, which limits the download.
* Bad, because the existing charts have to be rewritten for it.

### Vega-Lite

BSD-3-Clause. Very active, with 6.5.0 on 8 October 2026.

* Good, because its charts are JSON specifications.
* Bad, because the download is larger.
* Bad, because its expression engine needs `unsafe-eval` in the Content Security Policy unless the
  interpreter package is added.

### Plotly.js

MIT. Active, with 4.1.2 in October 2026.

* Bad, because it has the largest download and its own look.

### Chart.js

* Bad, because it had one release in the past year. The project owner ruled it out.

### Observable Plot, uPlot, D3, Highcharts, ApexCharts or IBM Carbon Charts

* Bad, because Observable Plot has had no release since February 2025.
* Bad, because uPlot has one maintainer and covers time series only.
* Bad, because D3 was last released in March 2024, and uPortal would have to write its own charts
  on top of it.
* Bad, because Highcharts has a commercial licence, and ApexCharts v8 uses its own licence file.
* Bad, because IBM Carbon Charts is built for the Carbon design system, not for Bootstrap.

## More Information

* Release dates come from the npm registry on 10 October 2026.
* [Apache ECharts](https://echarts.apache.org/)
* Prototype references: MOD-D34, MOD-I51.
