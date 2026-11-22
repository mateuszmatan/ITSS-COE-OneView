import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { Fields, check, line } from '../shared/fields';
import { fieldOf, helpOf, text, unexplained } from '../testing/dom';
import { FORM_FIELD } from './form-field';

@Component({
  imports: [ReactiveFormsModule, FORM_FIELD, Fields],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ov-form-field help="The team that looks after the product.">
      <ov-label>Owner team</ov-label>
      <input ovInput [formControl]="group.controls.team" />
    </ov-form-field>
    <ov-form-field>
      <ov-label>Plain</ov-label>
      <input ovInput [formControl]="group.controls.plain" />
    </ov-form-field>
    <ov-fields [group]="group" [fields]="fields" />
  `,
})
class Host {
  readonly group = new FormGroup({
    team: new FormControl(''),
    plain: new FormControl(''),
    name: new FormControl(''),
    enabled: new FormControl(false),
  });
  readonly fields = [
    line('name', 'Service name', '', 6, { help: 'Used in the job names.' }),
    check('enabled', 'Write metrics', 'influx.enabled', 6, { help: 'Sends the results.' }),
  ];
}

describe('OvHelp', () => {
  let fixture: ComponentFixture<Host>;

  beforeEach(async () => {
    fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
  });

  const page = () => fixture.nativeElement as HTMLElement;
  const trigger = (root: ParentNode) => root.querySelector<HTMLButtonElement>('.ov-help-trigger')!;
  const popover = () => document.querySelector<HTMLElement>('.ov-help-popover');

  async function fire(element: HTMLElement, type: string) {
    element.dispatchEvent(new Event(type));
    await fixture.whenStable();
  }

  async function pause() {
    await new Promise((resolve) => setTimeout(resolve, 200));
    await fixture.whenStable();
  }

  async function focusWithKeyboard(element: HTMLElement) {
    document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }));
    element.focus();
    await fixture.whenStable();
  }

  it('puts an info icon after the label of the fields that have help, and none on the others', () => {
    const field = fieldOf(page(), 'Owner team')!;

    expect(field.querySelector('.form-label-row .form-label + ov-help')).not.toBeNull();
    expect(fieldOf(page(), 'Plain')!.querySelector('ov-help')).toBeNull();
    expect(text(field.querySelector('label'))).toBe('Owner team');
    expect(popover()).toBeNull();
  });

  it('names the icon after its field and describes it with the help text for assistive technology', () => {
    const button = trigger(fieldOf(page(), 'Owner team')!);

    expect(button.type).toBe('button');
    expect(button.getAttribute('aria-label')).toBe('More information about Owner team');
    expect(trigger(page().querySelector('ov-checkbox')!).getAttribute('aria-label')).toBe(
      'More information about Write metrics',
    );
    expect(helpOf(fieldOf(page(), 'Owner team')!)).toBe('The team that looks after the product.');
    expect(unexplained(page())).toEqual(['Plain']);
  });

  it('shows the help on hover and keyboard focus, and Escape closes it', async () => {
    const button = trigger(fieldOf(page(), 'Owner team')!);

    await fire(button, 'mouseenter');

    expect(popover()?.getAttribute('role')).toBe('tooltip');
    expect(text(popover())).toBe('The team that looks after the product.');

    document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', keyCode: 27 }));
    await fixture.whenStable();

    expect(popover()).toBeNull();

    await focusWithKeyboard(button);

    expect(text(popover())).toBe('The team that looks after the product.');

    button.blur();
    await fixture.whenStable();

    expect(popover()).toBeNull();
  });

  it('keeps help opened by the keyboard while the pointer passes over the icon', async () => {
    const button = trigger(fieldOf(page(), 'Owner team')!);
    await focusWithKeyboard(button);
    await fire(button, 'mouseenter');
    await fire(button, 'mouseleave');
    await pause();

    expect(text(popover())).toBe('The team that looks after the product.');

    button.blur();
    await fixture.whenStable();
  });

  it('shows no help when the icon gets the focus from code or a click', async () => {
    await pause();
    const button = trigger(fieldOf(page(), 'Owner team')!);
    button.focus();
    await fixture.whenStable();

    expect(popover()).toBeNull();

    button.blur();
    button.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, buttons: 1, detail: 1 }));
    button.focus();
    await fixture.whenStable();

    expect(popover()).toBeNull();
  });

  it('keeps the help open while the pointer moves onto it', async () => {
    const button = trigger(fieldOf(page(), 'Owner team')!);
    await fire(button, 'mouseenter');
    await fire(button, 'mouseleave');
    await fire(popover()!, 'mouseenter');
    await pause();

    expect(popover()).not.toBeNull();

    await fire(popover()!, 'mouseleave');
    await pause();

    expect(popover()).toBeNull();
  });

  it('renders the help of fields and check boxes listed for ov-fields the same way', () => {
    const box = page().querySelector('ov-checkbox')!;

    expect(helpOf(fieldOf(page(), 'Service name')!)).toBe('Used in the job names.');
    expect(box.querySelector('.form-check-label + ov-help')).not.toBeNull();
    expect(helpOf(box)).toBe('Sends the results.');
    expect(text(box.querySelector('label'))).toBe('Write metrics (influx.enabled)');
  });
});
