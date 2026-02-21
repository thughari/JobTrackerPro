import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TocComponent } from './toc.component';

describe('TocComponent', () => {
  let component: TocComponent;
  let fixture: ComponentFixture<TocComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TocComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(TocComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
