/** 05 §4 FormationTemplate.slots。 */
export interface FormationSlot {
  slotKey: string;
  role: number;
  roleLabel: string;
  x: number;
  y: number;
}

/** 05 §4 FormationTemplate；预置模板不可改删。 */
export interface FormationTemplate {
  id: number;
  name: string;
  isPreset: boolean;
  baseTemplateId?: number | null;
  slots: FormationSlot[];
}

/** LIN-05 不分页集合。 */
export interface FormationTemplateListResponse {
  items: FormationTemplate[];
}

export interface FormationTemplateResponse {
  template: FormationTemplate;
}

export interface FormationTemplateCreateRequest {
  baseTemplateId: number;
  name: string;
}

export interface FormationTemplateRenameRequest {
  name: string;
}
