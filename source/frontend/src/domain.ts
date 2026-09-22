export const systemIds = ["OPERATION", "MAINTENANCE", "SUPPORT"] as const;
export type SystemId = (typeof systemIds)[number];

export type SystemDefinition = {
  id: SystemId;
  name: string;
  shortName: string;
  prefix: string;
  icon: string;
  eyebrow: string;
  sections: string[];
};

export const systems: SystemDefinition[] = [
  {
    id: "OPERATION",
    name: "装备使用及作战运用",
    shortName: "装备使用及作战运用",
    prefix: "/operation",
    icon: "Orbit",
    eyebrow: "OPERATION TRAINING",
    sections: ["本系统工作台", "资源准备", "教学制作", "培训组织", "训练评价"],
  },
  {
    id: "MAINTENANCE",
    name: "装备维修保障",
    shortName: "装备维修保障",
    prefix: "/maintenance",
    icon: "GraduationCap",
    eyebrow: "MAINTENANCE TRAINING",
    sections: ["本系统工作台", "资源准备", "教学制作", "培训组织", "训练评价"],
  },
  {
    id: "SUPPORT",
    name: "保障任务筹划及行为演练",
    shortName: "保障任务筹划及行为演练",
    prefix: "/support",
    icon: "Network",
    eyebrow: "SUPPORT OPERATIONS",
    sections: ["本系统工作台", "资源准备", "教学训练", "保障筹划", "保障演练", "执行归档"],
  },
];

export function systemFor(id?: SystemId | null) {
  return systems.find((system) => system.id === id);
}

export function systemName(id?: SystemId | null) {
  return systemFor(id)?.name || "全部系统";
}
