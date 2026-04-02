// ─── Cities ───────────────────────────────────────────────────
export type City = {
  key: string;
  displayName: string;
};

export const CITIES: City[] = [
  { key: "cdmx", displayName: "Valle de Mexico (CDMX + Edomex)" },
  { key: "monterrey", displayName: "Monterrey" },
  { key: "guadalajara", displayName: "Guadalajara" },
  { key: "puebla", displayName: "Puebla-Tlaxcala" },
  { key: "toluca", displayName: "Toluca" },
  { key: "tijuana", displayName: "Tijuana" },
  { key: "leon", displayName: "Leon" },
  { key: "queretaro", displayName: "Queretaro" },
  { key: "merida", displayName: "Merida" },
  { key: "cancun", displayName: "Cancun" },
  { key: "aguascalientes", displayName: "Aguascalientes" },
  { key: "chihuahua", displayName: "Chihuahua" },
  { key: "saltillo", displayName: "Saltillo" },
  { key: "hermosillo", displayName: "Hermosillo" },
  { key: "morelia", displayName: "Morelia" },
  { key: "culiacan", displayName: "Culiacan" },
  { key: "veracruz", displayName: "Veracruz" },
  { key: "villahermosa", displayName: "Villahermosa" },
  { key: "tuxtla", displayName: "Tuxtla Gutierrez" },
  { key: "oaxaca", displayName: "Oaxaca" },
  { key: "tampico", displayName: "Tampico" },
  { key: "reynosa", displayName: "Reynosa" },
  { key: "san_luis", displayName: "San Luis Potosi" },
  { key: "mexicali", displayName: "Mexicali" },
  { key: "cuernavaca", displayName: "Cuernavaca" },
  { key: "pachuca", displayName: "Pachuca" },
  { key: "xalapa", displayName: "Xalapa" },
  { key: "celaya", displayName: "Celaya" },
  { key: "irapuato", displayName: "Irapuato" },
  { key: "mazatlan", displayName: "Mazatlan" },
  { key: "durango", displayName: "Durango" },
  { key: "zacatecas", displayName: "Zacatecas" },
  { key: "campeche", displayName: "Campeche" },
  { key: "colima", displayName: "Colima" },
  { key: "tepic", displayName: "Tepic" },
  { key: "la_paz", displayName: "La Paz" },
  { key: "chetumal", displayName: "Chetumal" },
  { key: "coatzacoalcos", displayName: "Coatzacoalcos" },
  { key: "playa_del_carmen", displayName: "Playa del Carmen" },
  { key: "los_cabos", displayName: "Los Cabos" },
];

/** Set of valid city keys for quick lookup */
export const CITY_KEYS = new Set(CITIES.map((c) => c.key));

// ─── Platforms ────────────────────────────────────────────────
export type Platform = {
  key: string;
  displayName: string;
};

export const PLATFORMS: Platform[] = [
  { key: "uber", displayName: "Uber" },
  { key: "didi", displayName: "DiDi" },
  { key: "indrive", displayName: "InDrive" },
];

/** Set of valid platform keys for quick lookup */
export const PLATFORM_KEYS = new Set(PLATFORMS.map((p) => p.key));
