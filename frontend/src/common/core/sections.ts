export interface OneViewSection {
  path: string;
  label: string;
  heading: string;
  description: string;
}

export interface OneViewTab {
  path: string;
  label: string;
}

export interface AdminArea {
  section: OneViewSection;
  tabs: readonly OneViewTab[];
}

export interface OneViewArea {
  key: string;
  label: string;
  sections: readonly OneViewSection[];
}
