import { describe, expect, it, vi } from 'vite-plus/test';
import { ApiClient, ApiError } from '../../lib/api-client';
import {
    fetchSearchResults,
    fetchSuggestions,
    matchSuggestions,
    PORTLET_LIST_PATH,
    SEARCH_PATH,
    validatePortletList,
    validateSearchResults,
} from './search-api';

const BODY = {
    query: 'cartoon',
    groups: [
        { type: 'people', label: null, results: [], failed: false },
        {
            type: 'web',
            label: 'Campus web',
            results: [],
            failed: true,
        },
        {
            type: 'portlets',
            label: null,
            failed: false,
            results: [
                {
                    title: 'Daily Business Cartoon',
                    url: '/uPortal/p/daily-business-cartoon/max',
                    attributes: { name: ['Daily Business Cartoon'], score: ['4.0'] },
                },
            ],
        },
    ],
};

function api(response: Response) {
    const fetch = vi.fn<typeof globalThis.fetch>().mockResolvedValue(response);
    return { fetch, client: new ApiClient({ contextPath: '/uPortal', fetch }) };
}

describe('validateSearchResults', () => {
    it('accepts the search api body', () => {
        expect(validateSearchResults(BODY)).toEqual(BODY);
    });

    it('drops links that are not safe', () => {
        const body = structuredClone(BODY);
        const result = body.groups[2]?.results[0];
        if (result) {
            result.url = 'javascript:alert(1)';
        }
        expect(validateSearchResults(body).groups[2]?.results[0]?.url).toBeNull();
    });

    it('reads a group without a label or failed flag as a working, unnamed one', () => {
        expect(
            validateSearchResults({ query: 'x', groups: [{ type: 'people', results: [] }] })
                .groups[0],
        ).toEqual({ type: 'people', label: null, results: [], failed: false });
    });

    it('accepts a result without a url', () => {
        const people = { title: 'Steven Student', url: null, attributes: { sn: ['Student'] } };
        expect(
            validateSearchResults({ query: 'x', groups: [{ type: 'people', results: [people] }] })
                .groups[0]?.results[0],
        ).toEqual(people);
    });

    it.each([
        ['a body that is not an object', []],
        ['a missing query', { groups: [] }],
        ['groups that are not an array', { query: 'x', groups: {} }],
        ['a group without a type', { query: 'x', groups: [{ results: [] }] }],
        [
            'a failed flag that is not a boolean',
            { query: 'x', groups: [{ type: 't', results: [], failed: 'no' }] },
        ],
        [
            'a label that is not a string',
            { query: 'x', groups: [{ type: 't', results: [], label: 3 }] },
        ],
        ['a result that is not an object', { query: 'x', groups: [{ type: 't', results: [1] }] }],
        [
            'a url that is not a string',
            {
                query: 'x',
                groups: [{ type: 't', results: [{ title: 'a', url: 1, attributes: {} }] }],
            },
        ],
        [
            'an attribute that is not a list of strings',
            {
                query: 'x',
                groups: [{ type: 't', results: [{ title: 'a', attributes: { sn: 'Student' } }] }],
            },
        ],
        [
            'an attribute list with a number',
            {
                query: 'x',
                groups: [{ type: 't', results: [{ title: 'a', attributes: { sn: ['a', 1] } }] }],
            },
        ],
    ])('refuses %s', (_name, body) => {
        expect(() => validateSearchResults(body)).toThrow(TypeError);
    });
});

describe('fetchSearchResults', () => {
    it('asks for the query and each type', async () => {
        const { fetch, client } = api(Response.json(BODY));

        await expect(fetchSearchResults(client, 'a&b', ['people', 'portlets'])).resolves.toEqual(
            BODY,
        );

        expect(fetch).toHaveBeenCalledWith(
            `/uPortal${SEARCH_PATH}?q=a%26b&type=people&type=portlets`,
            expect.objectContaining({ method: 'GET' }),
        );
    });

    it('fails as malformed when the body is not search results', async () => {
        const { client } = api(Response.json({ query: 'x' }));

        await expect(fetchSearchResults(client, 'x', [])).rejects.toMatchObject({
            kind: 'malformed',
        });
    });
});

const CARTOON = {
    id: '3',
    fname: 'daily-business-cartoon',
    name: 'Daily Business Cartoon',
    title: 'Daily Business Cartoon',
    description: 'Daily Business Cartoon by Ted Goff, www.tedgoff.com',
};
const CALENDAR = { id: '7', fname: 'calendar', title: 'Calendar', description: null };

const REGISTRY = {
    registry: {
        channels: [],
        categories: [
            {
                channels: [CARTOON],
                categories: [{ channels: [CALENDAR, CARTOON], categories: [] }],
            },
        ],
    },
};

describe('validatePortletList', () => {
    it('lists each portlet once, nested categories included', () => {
        expect(validatePortletList(REGISTRY)).toEqual([
            {
                fname: 'daily-business-cartoon',
                title: 'Daily Business Cartoon',
                description: 'Daily Business Cartoon by Ted Goff, www.tedgoff.com',
                label: 'Daily Business Cartoon~Daily Business Cartoon~daily-business-cartoon~Daily Business Cartoon by Ted Goff, www.tedgoff.com',
            },
            { fname: 'calendar', title: 'Calendar', description: '', label: '~Calendar~calendar~' },
        ]);
    });

    it.each([
        ['a body without a registry', {}],
        ['a registry without categories', { registry: { channels: [] } }],
        [
            'a portlet without an fname',
            { registry: { channels: [{ title: 'x' }], categories: [] } },
        ],
    ])('refuses %s', (_name, body) => {
        expect(() => validatePortletList(body)).toThrow(TypeError);
    });
});

describe('fetchSuggestions', () => {
    it('reads the portlet list', async () => {
        const { fetch, client } = api(Response.json(REGISTRY));

        await expect(fetchSuggestions(client)).resolves.toHaveLength(2);

        expect(fetch).toHaveBeenCalledWith(`/uPortal${PORTLET_LIST_PATH}`, expect.anything());
    });

    it('fails with the api error', async () => {
        const { client } = api(new Response('', { status: 500 }));

        await expect(fetchSuggestions(client)).rejects.toBeInstanceOf(ApiError);
    });
});

describe('matchSuggestions', () => {
    const suggestions = validatePortletList(REGISTRY);

    it('matches any part of the label, ignoring case', () => {
        expect(matchSuggestions(suggestions, 'CARTOO').map((s) => s.fname)).toEqual([
            'daily-business-cartoon',
        ]);
        expect(matchSuggestions(suggestions, 'tedgoff').map((s) => s.fname)).toEqual([
            'daily-business-cartoon',
        ]);
        expect(matchSuggestions(suggestions, 'a').map((s) => s.fname)).toEqual([
            'daily-business-cartoon',
            'calendar',
        ]);
    });

    it('matches nothing for a blank term or one without matches', () => {
        expect(matchSuggestions(suggestions, '  ')).toEqual([]);
        expect(matchSuggestions(suggestions, 'zzqxjv')).toEqual([]);
    });
});
