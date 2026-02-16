import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ResourcesComponent } from './resources.component';
import { ResourceService } from '../../services/resource.service';
import { AuthService } from '../../services/auth.service';

describe('ResourcesComponent', () => {
  let component: ResourcesComponent;
  let fixture: ComponentFixture<ResourcesComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ResourcesComponent],
      providers: [
        {
          provide: ResourceService,
          useValue: {
            getResources: () => Promise.resolve([]),
            createResource: () => Promise.resolve({}),
            deleteResource: () => Promise.resolve(),
            updateResource: () => Promise.resolve({}),
          },
        },
        {
          provide: AuthService,
          useValue: {
            isAuthenticated: () => false,
            currentUser: () => null,
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ResourcesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
