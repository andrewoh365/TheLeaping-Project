export interface ChartDataset {
  label: string;
  data: number[];
  borderColor: string;
  backgroundColor: string;
  tension: number;
  fill: boolean;
}

export interface ChartConfig {
  labels: string[];
  datasets: ChartDataset[];
}