import { ComponentFixture, TestBed } from '@angular/core/testing';

import { GmailSetupModalComponent } from './gmail-setup-modal.component';

describe('GmailSetupModalComponent', () => {
  let component: GmailSetupModalComponent;
  let fixture: ComponentFixture<GmailSetupModalComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [GmailSetupModalComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(GmailSetupModalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
