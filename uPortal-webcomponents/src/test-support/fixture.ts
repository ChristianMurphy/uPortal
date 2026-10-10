import type { LitElement } from 'lit';

const containers: HTMLElement[] = [];

/** Attaches the markup to the document and waits for the first element to render. */
export async function fixture<T extends LitElement>(html: string): Promise<T> {
    const container = document.createElement('div');
    container.innerHTML = html;
    document.body.append(container);
    containers.push(container);
    const element = container.firstElementChild as T;
    await element.updateComplete;
    return element;
}

/** Removes every fixture; call from afterEach. */
export function cleanupFixtures(): void {
    for (const container of containers.splice(0)) {
        container.remove();
    }
}
