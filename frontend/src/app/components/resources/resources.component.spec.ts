import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ResourcesComponent } from './resources.component';
import { ResourceService } from '../../services/resource.service';
import { AuthService } from '../../services/auth.service';
import { provideRouter } from '@angular/router';

describe('ResourcesComponent', () => {
  let component: ResourcesComponent;
  let fixture: ComponentFixture<ResourcesComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ResourcesComponent],
      providers: [
        provideRouter([]),
        {
          provide: ResourceService,
          useValue: {
            getResources: () => Promise.resolve({
              content: [],
              page: 0,
              size: 20,
              totalElements: 0,
              totalPages: 0,
              hasNext: false,
            }),
            createResource: () => Promise.resolve({}),
            uploadResourceFile: () => Promise.resolve({}),
            deleteResource: () => Promise.resolve(),
          },
        },
        {
          provide: AuthService,
          useValue: {
            isAuthenticated: () => false,
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
