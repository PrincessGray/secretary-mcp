// src/main/ts/src/types/models.ts

// 连接类型枚举
export enum ConnectionType {
  STDIO = "STDIO",
  SSE = "SSE"
}

// 管理类型枚举
export enum ManagementType {
  NPX = "NPX",
  NODE = "NODE",
  PYTHON = "PYTHON"
}

// 任务状态枚举
export enum TaskStatus {
  ACTIVE = "ACTIVE",
  INACTIVE = "INACTIVE",
  ERROR = "ERROR"
}

// 参数配置类别枚举
export enum ConfigParamCategory {
  STDIO_ENV = "STDIO_ENV",
  STDIO_ARG = "STDIO_ARG",
  SSE_CONNECTION = "SSE_CONNECTION"
}

// 通用配置
export interface GeneralConfig {
  timeoutSeconds?: number;
  retryCount?: number;
  retryDelaySeconds?: number;
  enableRoots?: boolean;
  enableSampling?: boolean;
  loggingLevel?: string;
  maxMemoryMb?: number;
  maxCpuPercent?: number;
  maxDiskSpaceMb?: number;
  verboseMode?: boolean;
  enableDryRun?: boolean;
  outputFormat?: string;
  customSettings?: Record<string, any>;
}

// SSE配置
export interface SseConfig {
  serverUrl: string;
  bearerToken?: string;
}

// STDIO配置
export interface StdioConfig {
  command: string;
  commandArgs?: string[];
  environmentVars?: Record<string, string>;
  managementType?: ManagementType;
  workingDir?: string;
}

// 连接配置
export interface ConnectionProfile {
  connectionType: ConnectionType;
  connectionTimeoutSeconds?: number;
  stdioConfig?: StdioConfig;
  sseConfig?: SseConfig;
  generalConfig?: GeneralConfig;
}

// 配置参数
export interface ConfigParam {
  name: string;
  displayName: string;
  description: string;
  type: string;
  required: boolean;
  defaultValue?: any;
  category: ConfigParamCategory;
}

// 秘书
export interface Secretary {
  id: string;
  name: string;
  description: string;
  createdAt: string;
  updatedAt: string;
  taskIds: string[];
  active: boolean;
}

// 秘书信息(轻量版)
export interface SecretaryInfo {
  id: string;
  name: string;
  description: string;
  active: boolean;
}

// 任务模板
export interface TaskTemplate {
  id: string;
  name: string;
  description: string;
  createdAt: string;
  updatedAt: string;
  connectionProfile: ConnectionProfile;
  customizableParams: ConfigParam[];
  defaultConfig: Record<string, any>;
  metadata?: Record<string, any>;
}

// 模板信息(轻量版)
export interface TemplateInfo {
  id: string;
  name: string;
  description: string;
  connectionType: ConnectionType;
  managementType: ManagementType;
}

// 远程任务
export interface RemoteTask {
  id: string;
  name: string;
  description: string;
  secretaryId: string;
  secretaryName: string;
  templateId: string;
  status: TaskStatus;
  createdAt: string;
  updatedAt: string;
  connectionProfile: ConnectionProfile;
  customizableParams: ConfigParam[];
  config: Record<string, any>;
  metadata?: Record<string, any>;
}
