import React from "react";

export interface NavTab {
  key: string;
  label: string;
  /** One of: "home" | "list" | "play" | "calendar" | "settings". */
  icon: string;
}

/**
 * @startingPoint section="Navigation" subtitle="App bottom navigation bar" viewport="420x80"
 */
export interface BottomNavProps {
  /** Defaults to the five Kompara tabs (Inicio · Comparar · Lector · Fiscal · Ajustes). */
  tabs?: NavTab[];
  /** Key of the active tab. */
  current?: string;
  onSelect?: (key: string) => void;
  style?: React.CSSProperties;
}

/** The five Kompara destinations, in bar order. */
export declare const KOMPARA_TABS: NavTab[];

/**
 * App bottom navigation: five flat tabs, active one tinted brand-green.
 */
export function BottomNav(props: BottomNavProps): React.JSX.Element;
