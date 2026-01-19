import {
  Component,
  ElementRef,
  Input,
  OnChanges,
  SimpleChanges,
  ViewChild,
  ViewEncapsulation,
  AfterViewInit,
  OnDestroy,
} from '@angular/core';
import * as d3 from 'd3';

@Component({
  selector: 'app-donut-chart',
  standalone: true,
  imports: [],
  templateUrl: './donut-chart.component.html',
  styleUrl: './donut-chart.component.css',
  encapsulation: ViewEncapsulation.None,
})
export class DonutChartComponent
  implements OnChanges, AfterViewInit, OnDestroy
{
  @Input() data: { name: string; value: number }[] = [];
  @Input() colors: string[] = [
    '#6366f1',
    '#a855f7',
    '#ec4899',
    '#ef4444',
    '#f59e0b',
  ];
  @Input() innerRadiusRatio = 0.6;
  @Input() strokeColor: string = '#151A23';

  @ViewChild('chartContainer', { static: true })
  private chartContainer!: ElementRef;
  private resizeObserver: ResizeObserver | undefined;
  private animationFrameId: number | undefined;

  ngAfterViewInit() {
    this.resizeObserver = new ResizeObserver(() => {
      if (this.animationFrameId) {
        cancelAnimationFrame(this.animationFrameId);
      }
      this.animationFrameId = requestAnimationFrame(() => {
        this.createChart();
      });
    });
    this.resizeObserver.observe(this.chartContainer.nativeElement);
  }

  ngOnDestroy() {
    if (this.animationFrameId) {
      cancelAnimationFrame(this.animationFrameId);
    }
    this.resizeObserver?.disconnect();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if ((changes['data'] && this.data) || changes['strokeColor']) {
      if (this.animationFrameId) {
        cancelAnimationFrame(this.animationFrameId);
      }
      this.animationFrameId = requestAnimationFrame(() => {
        this.createChart();
      });
    }
  }

  private createChart(): void {
    if (!this.chartContainer) return;
    const element = this.chartContainer.nativeElement;

    d3.select(element).selectAll('*').remove();

    if (!this.data || this.data.length === 0) {
      this.renderEmptyChart(element);
      return;
    }

    const totalValue = this.data.reduce((acc, curr) => acc + curr.value, 0);
    if (totalValue === 0) {
      this.renderEmptyChart(element);
      return;
    }

    const rect = element.getBoundingClientRect();
    const width = rect.width;
    const height = rect.height;

    if (width === 0 || height === 0) return;

    const margin = 10;
    const radius = Math.min(width, height) / 2 - margin;

    const svg = d3
      .select(element)
      .append('svg')
      .attr('width', width)
      .attr('height', height)
      .style('display', 'block')
      .append('g')
      .attr('transform', `translate(${width / 2},${height / 2})`);

    const color = d3
      .scaleOrdinal()
      .domain(this.data.map((d) => d.name))
      .range(this.colors);

    const pie = d3
      .pie<{ name: string; value: number }>()
      .sort(null)
      .value((d) => d.value);

    const data_ready = pie(this.data);

    const arc = d3
      .arc()
      .innerRadius(radius * this.innerRadiusRatio)
      .outerRadius(radius);

    const hoverArc = d3
      .arc()
      .innerRadius(radius * this.innerRadiusRatio)
      .outerRadius(radius + 5);

    const tooltip = d3
      .select(element)
      .append('div')
      .style('position', 'absolute')
      .style('background', '#1f2937')
      .style('color', '#fff')
      .style('padding', '5px 10px')
      .style('border-radius', '4px')
      .style('font-size', '12px')
      .style('pointer-events', 'none')
      .style('opacity', 0)
      .style('border', '1px solid #374151')
      .style('box-shadow', '0 4px 6px -1px rgba(0, 0, 0, 0.1)')
      .style('z-index', '10');

    const paths = svg
      .selectAll('allSlices')
      .data(data_ready)
      .enter()
      .append('path')
      .attr('d', arc as any)
      .attr('fill', (d) => color(d.data.name) as string)
      .attr('stroke', this.strokeColor)
      .style('stroke-width', '2px')
      .style('cursor', 'pointer');

    paths
      .on('mouseover', function (event, d) {
        d3.select(this)
          .transition()
          .duration(200)
          .attr('d', hoverArc as any);
        tooltip.transition().duration(200).style('opacity', 1);
        tooltip
          .html(`${d.data.name}: <strong>${d.data.value}</strong>`)
          .style('left', event.offsetX + 10 + 'px')
          .style('top', event.offsetY - 10 + 'px');
      })
      .on('mousemove', function (event) {
        tooltip
          .style('left', event.offsetX + 10 + 'px')
          .style('top', event.offsetY - 10 + 'px');
      })
      .on('mouseout', function (event, d) {
        d3.select(this)
          .transition()
          .duration(200)
          .attr('d', arc as any);
        tooltip.transition().duration(200).style('opacity', 0);
      });

    paths
      .transition()
      .duration(1000)
      .attrTween('d', function (d) {
        const i = d3.interpolate({ ...d, endAngle: d.startAngle }, d);
        return function (t) {
          return arc(i(t) as any) as string;
        };
      });
  }

  private renderEmptyChart(element: HTMLElement) {
    const rect = element.getBoundingClientRect();
    const width = rect.width || 300;
    const height = rect.height || 300;
    const margin = 10;
    const radius = Math.min(width, height) / 2 - margin;

    const svg = d3
      .select(element)
      .append('svg')
      .attr('width', width)
      .attr('height', height)
      .style('display', 'block')
      .append('g')
      .attr('transform', `translate(${width / 2},${height / 2})`);

    const arc = d3
      .arc()
      .innerRadius(radius * this.innerRadiusRatio)
      .outerRadius(radius)
      .startAngle(0)
      .endAngle(2 * Math.PI);

    svg
      .append('path')
      .attr('d', arc as any)
      .attr('fill', this.strokeColor === '#151A23' ? '#1f2937' : '#e5e7eb')
      .style('opacity', 0.3);

    svg
      .append('text')
      .attr('text-anchor', 'middle')
      .attr('dy', '0.35em')
      .text('No Data')
      .attr('fill', this.strokeColor === '#151A23' ? '#6b7280' : '#9ca3af')
      .style('font-size', '12px');
  }
}
