import { describe, expect, it } from 'vite-plus/test';
import type { PopularPortlet } from './popular-portlets-api';
import {
    applyFilters,
    applySort,
    byCodeUnits,
    DEFAULT_SORT,
    filterOptions,
    nextSort,
    NO_FILTERS,
    pageButtons,
    tableView,
} from './table';

function portlet(fname: string, count: number): PopularPortlet {
    return { fname, title: fname.toUpperCase(), description: null, url: `/p/${fname}`, count };
}

// In the order the API sends them: most added first, ties by fname
const PORTLETS = [
    portlet('xkcd', 12),
    portlet('Bookmarks', 3),
    portlet('calendar', 3),
    portlet('what-is-uportal', 3),
    portlet('uportal-links', 1),
];

const fnames = (portlets: readonly PopularPortlet[]) => portlets.map((p) => p.fname);

describe('byCodeUnits', () => {
    it('sorts as JavaScript does by default', () => {
        const values = ['b', 'B', 'a-b', 'a', '10', '9'];
        expect(values.toSorted(byCodeUnits)).toEqual(values.toSorted());
        expect(byCodeUnits('a', 'a')).toBe(0);
    });
});

describe('applySort', () => {
    it('sorts the Title column by fname, ignoring case', () => {
        expect(fnames(applySort(PORTLETS, DEFAULT_SORT))).toEqual([
            'Bookmarks',
            'calendar',
            'uportal-links',
            'what-is-uportal',
            'xkcd',
        ]);
        expect(fnames(applySort(PORTLETS, { column: 'title', direction: 'descending' }))).toEqual([
            'xkcd',
            'what-is-uportal',
            'uportal-links',
            'calendar',
            'Bookmarks',
        ]);
    });

    it('sorts the count column by number, keeping the order of equal counts', () => {
        expect(fnames(applySort(PORTLETS, { column: 'count', direction: 'ascending' }))).toEqual([
            'uportal-links',
            'Bookmarks',
            'calendar',
            'what-is-uportal',
            'xkcd',
        ]);
        expect(fnames(applySort(PORTLETS, { column: 'count', direction: 'descending' }))).toEqual([
            'xkcd',
            'Bookmarks',
            'calendar',
            'what-is-uportal',
            'uportal-links',
        ]);
    });
});

describe('nextSort', () => {
    it('turns the sorted column around and sorts another one ascending', () => {
        expect(nextSort(DEFAULT_SORT, 'title')).toEqual({
            column: 'title',
            direction: 'descending',
        });
        expect(nextSort({ column: 'title', direction: 'descending' }, 'title')).toEqual(
            DEFAULT_SORT,
        );
        expect(nextSort({ column: 'title', direction: 'descending' }, 'count')).toEqual({
            column: 'count',
            direction: 'ascending',
        });
    });
});

describe('filters', () => {
    it('offers each fname and each count once, in code-unit order', () => {
        expect(filterOptions(PORTLETS, 'title')).toEqual([
            'Bookmarks',
            'calendar',
            'uportal-links',
            'what-is-uportal',
            'xkcd',
        ]);
        // As text, as the portlet's filter listed them
        expect(filterOptions(PORTLETS, 'count')).toEqual(['1', '12', '3']);
    });

    it('keeps rows that match any value of a column and every filtered column', () => {
        expect(applyFilters(PORTLETS, NO_FILTERS)).toEqual(PORTLETS);
        expect(fnames(applyFilters(PORTLETS, { title: ['xkcd', 'calendar'], count: [] }))).toEqual([
            'xkcd',
            'calendar',
        ]);
        expect(fnames(applyFilters(PORTLETS, { title: [], count: ['3', '1'] }))).toEqual([
            'Bookmarks',
            'calendar',
            'what-is-uportal',
            'uportal-links',
        ]);
        expect(
            fnames(applyFilters(PORTLETS, { title: ['xkcd', 'calendar'], count: ['3'] })),
        ).toEqual(['calendar']);
    });
});

describe('tableView', () => {
    it('shows the first page', () => {
        expect(tableView(PORTLETS, NO_FILTERS, DEFAULT_SORT, 2, 0)).toEqual({
            rows: [PORTLETS[1], PORTLETS[2]],
            total: 5,
            page: 0,
            pageCount: 3,
            from: 1,
            to: 2,
        });
    });

    it('shows a short last page', () => {
        const view = tableView(PORTLETS, NO_FILTERS, DEFAULT_SORT, 2, 2);
        expect(fnames(view.rows)).toEqual(['xkcd']);
        expect([view.page, view.from, view.to]).toEqual([2, 5, 5]);
    });

    it('keeps the page within the pages there are', () => {
        expect(tableView(PORTLETS, NO_FILTERS, DEFAULT_SORT, 2, 9).page).toBe(2);
        expect(tableView(PORTLETS, NO_FILTERS, DEFAULT_SORT, 2, -1).page).toBe(0);
    });

    it('counts filtered rows only', () => {
        const view = tableView(PORTLETS, { title: ['xkcd'], count: [] }, DEFAULT_SORT, 10, 0);
        expect([view.total, view.pageCount, view.from, view.to]).toEqual([1, 1, 1, 1]);
    });

    it('has one empty page without rows', () => {
        expect(tableView([], NO_FILTERS, DEFAULT_SORT, 10, 0)).toEqual({
            rows: [],
            total: 0,
            page: 0,
            pageCount: 1,
            from: 0,
            to: 0,
        });
    });
});

describe('pageButtons', () => {
    it('shows every page up to seven', () => {
        expect(pageButtons(0, 1)).toEqual([0]);
        expect(pageButtons(3, 7)).toEqual([0, 1, 2, 3, 4, 5, 6]);
    });

    it('shows the first pages, a gap and the last near the start', () => {
        expect(pageButtons(0, 20)).toEqual([0, 1, 2, 3, 4, null, 19]);
        expect(pageButtons(3, 20)).toEqual([0, 1, 2, 3, 4, null, 19]);
    });

    it('shows the pages around the current one in the middle', () => {
        expect(pageButtons(4, 20)).toEqual([0, null, 3, 4, 5, null, 19]);
        expect(pageButtons(15, 20)).toEqual([0, null, 14, 15, 16, null, 19]);
    });

    it('shows the first, a gap and the last pages near the end', () => {
        expect(pageButtons(16, 20)).toEqual([0, null, 15, 16, 17, 18, 19]);
        expect(pageButtons(19, 20)).toEqual([0, null, 15, 16, 17, 18, 19]);
    });
});
