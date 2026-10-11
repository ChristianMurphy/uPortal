/**
 * What the popular portlets table shows: pure functions over the loaded portlets and the user's
 * sort, filter and paging choices. They follow the DataTables table of the Popular Portlets
 * portlet: the Title column sorts and filters by fname, filters keep a row when it matches any
 * chosen value of each filtered column, and the paging shows the page number buttons around the
 * current page.
 */
import type { PopularPortlet } from './popular-portlets-api';

export type Column = 'title' | 'count';
export type Direction = 'ascending' | 'descending';

export interface Sort {
    column: Column;
    direction: Direction;
}

/** The chosen filter values of each column: fnames for Title, counts for # Times. */
export interface Filters {
    title: readonly string[];
    count: readonly string[];
}

export const PAGE_SIZES = [5, 10, 20, 50] as const;
export const DEFAULT_PAGE_SIZE = 10;
export const DAY_CHOICES = [1, 7, 30, 90, 365] as const;
export const DEFAULT_DAYS = 30;
export const NO_FILTERS: Filters = { title: [], count: [] };
export const DEFAULT_SORT: Sort = { column: 'title', direction: 'ascending' };

/** JavaScript's default string order (UTF-16 code units), which DataTables' filters used. */
export function byCodeUnits(a: string, b: string): number {
    if (a < b) {
        return -1;
    }
    return a > b ? 1 : 0;
}

/** The value a column filters and sorts on. */
export function valueOf(portlet: PopularPortlet, column: Column): string {
    return column === 'title' ? portlet.fname : String(portlet.count);
}

export function applyFilters(
    portlets: readonly PopularPortlet[],
    filters: Filters,
): PopularPortlet[] {
    return portlets.filter(
        (portlet) =>
            (filters.title.length === 0 || filters.title.includes(portlet.fname)) &&
            (filters.count.length === 0 || filters.count.includes(String(portlet.count))),
    );
}

/** A stable sort: rows that compare equal keep the order the portal sent them in. */
export function applySort(portlets: readonly PopularPortlet[], sort: Sort): PopularPortlet[] {
    const sign = sort.direction === 'ascending' ? 1 : -1;
    const compare =
        sort.column === 'count'
            ? (a: PopularPortlet, b: PopularPortlet) => a.count - b.count
            : (a: PopularPortlet, b: PopularPortlet) =>
                  byCodeUnits(a.fname.toLowerCase(), b.fname.toLowerCase());
    return portlets.toSorted((a, b) => sign * compare(a, b));
}

/** The values a column's filter offers: each distinct value once, in code-unit order. */
export function filterOptions(portlets: readonly PopularPortlet[], column: Column): string[] {
    return [...new Set(portlets.map((portlet) => valueOf(portlet, column)))].toSorted(byCodeUnits);
}

/** Clicking a column header: the other column sorts ascending, the same one turns around. */
export function nextSort(current: Sort, column: Column): Sort {
    if (current.column !== column) {
        return { column, direction: 'ascending' };
    }
    return {
        column,
        direction: current.direction === 'ascending' ? 'descending' : 'ascending',
    };
}

export interface TableView {
    /** The rows of the current page. */
    rows: PopularPortlet[];
    /** Rows after filtering, on all pages. */
    total: number;
    /** 0-based, within the pages there are. */
    page: number;
    pageCount: number;
    /** 1-based number of the first row shown, 0 when there are none. */
    from: number;
    /** 1-based number of the last row shown, 0 when there are none. */
    to: number;
}

export function tableView(
    portlets: readonly PopularPortlet[],
    filters: Filters,
    sort: Sort,
    pageSize: number,
    page: number,
): TableView {
    const filtered = applySort(applyFilters(portlets, filters), sort);
    const total = filtered.length;
    const pageCount = Math.max(1, Math.ceil(total / pageSize));
    const current = Math.min(Math.max(page, 0), pageCount - 1);
    const start = current * pageSize;
    const rows = filtered.slice(start, start + pageSize);
    return {
        rows,
        total,
        page: current,
        pageCount,
        from: rows.length === 0 ? 0 : start + 1,
        to: start + rows.length,
    };
}

/**
 * The page buttons to show, 0-based, with `null` for a gap: every page up to seven, else the
 * first and last pages and those around the current one, as DataTables shows them.
 */
export function pageButtons(page: number, pageCount: number): (number | null)[] {
    const all = Array.from({ length: pageCount }, (_, index) => index);
    if (pageCount <= 7) {
        return all;
    }
    if (page < 4) {
        return [...all.slice(0, 5), null, pageCount - 1];
    }
    if (page > pageCount - 5) {
        return [0, null, ...all.slice(pageCount - 5)];
    }
    return [0, null, page - 1, page, page + 1, null, pageCount - 1];
}
