import {
  AfterViewInit,
  Component,
  ElementRef,
  Input,
  OnChanges,
  OnDestroy,
  SimpleChanges,
  ViewChild,
  ViewEncapsulation,
} from '@angular/core';
import * as d3 from 'd3';

@Component({
  selector: 'app-bar-chart',
  standalone: true,
  imports: [],
  templateUrl: './bar-chart.component.html',
  styleUrl: './bar-chart.component.css',
  encapsulation: ViewEncapsulation.None,
})
export class BarChartComponent implements OnChanges, AfterViewInit, OnDestroy {
  @Input() data: { name: string; value: number }[] = [];
  @Input() color: string = '#6366f1';
  @Input() gridColor: string = '#374151';
  @Input() textColor: string = '#6b7280';

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
    if (
      (changes['data'] && this.data) ||
      changes['gridColor'] ||
      changes['textColor']
    ) {
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
      return;
    }

    const rect = element.getBoundingClientRect();
    const clientWidth = rect.width;
    const clientHeight = rect.height;

    if (clientWidth === 0 || clientHeight === 0) return;

    const margin = { top: 20, right: 10, bottom: 30, left: 30 };
    const width = clientWidth - margin.left - margin.right;
    const height = clientHeight - margin.top - margin.bottom;

    if (width <= 0 || height <= 0) return;

    const svg = d3
      .select(element)
      .append('svg')
      .attr('width', clientWidth)
      .attr('height', clientHeight)
      .style('display', 'block')
      .append('g')
      .attr('transform', `translate(${margin.left},${margin.top})`);

    // X axis
    const x = d3
      .scaleBand()
      .range([0, width])
      .domain(this.data.map((d) => d.name))
      .padding(0.3);

    svg
      .append('g')
      .attr('transform', `translate(0,${height})`)
      .call(d3.axisBottom(x).tickSize(0).tickPadding(10))
      .select('.domain')
      .remove();

    svg
      .selectAll('.tick text')
      .attr('fill', this.textColor)
      .style('font-size', '11px');

    // Y axis
    const y = d3
      .scaleLinear()
      .domain([0, (d3.max(this.data, (d) => d.value) || 10) * 1.2])
      .range([height, 0]);

    svg
      .append('g')
      .call(d3.axisLeft(y).ticks(5).tickSize(-width))
      .call((g) => g.select('.domain').remove())
      .call((g) =>
        g
          .selectAll('.tick line')
          .attr('stroke', this.gridColor)
          .attr('stroke-dasharray', '2,2')
          .attr('opacity', 0.5)
      );

    svg
      .selectAll('.tick text')
      .attr('fill', this.textColor)
      .style('font-size', '11px');

    // Tooltip
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
      .style('z-index', '10');

    // Bars
    svg
      .selectAll('mybar')
      .data(this.data)
      .enter()
      .append('rect')
      .attr('x', (d) => x(d.name)!)
      .attr('width', x.bandwidth())
      .attr('fill', this.color)
      .attr('rx', 4)
      .attr('y', height)
      .attr('height', 0)
      .on('mouseover', function (event, d) {
        d3.select(this).attr('opacity', 0.8);
        tooltip.transition().duration(200).style('opacity', 1);
        tooltip
          .html(`${d.name}: <strong>${d.value}</strong>`)
          .style('left', event.offsetX + 'px')
          .style('top', event.offsetY - 30 + 'px');
      })
      .on('mousemove', function (event) {
        tooltip
          .style('left', event.offsetX + 'px')
          .style('top', event.offsetY - 30 + 'px');
      })
      .on('mouseout', function (event, d) {
        d3.select(this).attr('opacity', 1);
        tooltip.transition().duration(200).style('opacity', 0);
      })
      .transition()
      .duration(800)
      .delay((d, i) => i * 100)
      .attr('y', (d) => y(d.value))
      .attr('height', (d) => height - y(d.value));
  }
}
