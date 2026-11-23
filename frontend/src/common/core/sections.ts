export interface OneViewSection {
  path: string;
  label: string;
  heading: string;
  description: string;
}

export interface OneViewTab {
  path: string;
  label: string;
  /** The area or part the tab belongs to; tabs next to each other with the same group are shown under it. */
  group?: string;
}

export interface AdminArea {
  section: OneViewSection;
  tabs: readonly OneViewTab[];
}

export interface OneViewArea {
  key: string;
  label: string;
  path: string;
  description: string;
  sections: readonly OneViewSection[];
}
