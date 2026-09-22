export const trainingObjects = [
  { id: "PUMP-01", name: "通用泵组", icon: "Settings2" },
  { id: "VALVE-01", name: "阀门", icon: "CircleGauge" },
  { id: "CTRL-01", name: "控制单元", icon: "Monitor" },
  { id: "SENSOR-01", name: "传感器", icon: "Radar" },
];
export const objectName = (id: string) =>
  trainingObjects.find((o) => o.id === id)?.name || id || "尚未选择";
export function lessonStep(step: any) {
  if (!step) return null;
  const label = step.actionLabel || step.name;
  const legacy =
    !step.description ||
    step.description === "确认当前对象和前置步骤后执行模拟操作。";
  return {
    ...step,
    actionId: step.actionId || "",
    actionLabel: label,
    description: legacy
      ? `选择${objectName(step.target || "PUMP-01")}，点击“${label}”。系统将核对操作对象和步骤顺序。`
      : step.description,
    expectedResult:
      step.expectedResult || `${step.name}已完成，操作记录已保存。`,
    target: step.target || "PUMP-01",
  };
}
