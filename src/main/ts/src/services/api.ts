// src/main/ts/src/services/api.ts
import axios, { AxiosInstance, AxiosResponse } from 'axios';
import { Secretary, SecretaryInfo, TaskTemplate, TemplateInfo, RemoteTask } from '@/types/models';

// API基础URL
const API_BASE_URL = '/api';

// 创建API实例
const api: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json'
  },
  withCredentials: true
});

// 拦截器
api.interceptors.request.use(config => config, error => Promise.reject(error));
api.interceptors.response.use(
  (response: AxiosResponse) => response.data,
  error => Promise.reject(error)
);


// API函数
export const secretaryApi = {
  fetchAll: (): Promise<SecretaryInfo[]> => 
    api.get('/secretaries'),
    
  fetchById: (id: string): Promise<Secretary> => 
    api.get(`/secretaries/${id}`),
    
  create: (name: string, description?: string): Promise<Secretary> => 
    api.post('/secretaries', { name, description }),
    
  update: (id: string, data: Partial<Secretary>): Promise<Secretary> => 
    api.put(`/secretaries/${id}`, data),
    
  delete: (id: string): Promise<void> => 
    api.delete(`/secretaries/${id}`),
    
  activate: (id: string): Promise<void> => 
    api.post(`/secretaries/${id}/activate`),
    
  deactivate: (id: string): Promise<void> => 
    api.post(`/secretaries/${id}/deactivate`)
};

export const userSecretaryApi = {
  register: (userId: string, secretaryName: string): Promise<void> => 
    api.post('/user-secretary/register', null, { params: { userId, secretaryName } }),
  
  getSecretariesForUser: (userId: string): Promise<Secretary[]> => 
    api.get('/user-secretary/secretaries', { params: { userId } }),
    
  getAllMappings: (): Promise<Record<string, string[]>> => 
    api.get('/user-secretary/secretary-mappings'),
    
  unregister: (userId: string, secretaryName: string): Promise<void> =>
    api.delete('/user-secretary/unregister-specific', { params: { userId, secretaryName } }),
    
  unregisterAll: (userId: string): Promise<void> =>
    api.delete('/user-secretary/unregister', { params: { userId } })
};

export const templateApi = {
  fetchAll: (): Promise<TemplateInfo[]> => 
    api.get('/templates'),
  
  fetchById: (id: string): Promise<TaskTemplate> => 
    api.get(`/templates/${id}`),
  
  create: (template: Omit<TaskTemplate, 'id' | 'createdAt' | 'updatedAt'>): Promise<TaskTemplate> => 
    api.post('/templates', template),
  
  update: (id: string, template: Partial<TaskTemplate>): Promise<TaskTemplate> => 
    api.put(`/templates/${id}`, template),
  
  delete: (id: string): Promise<void> => 
    api.delete(`/templates/${id}`),
  
  getTemplateParams: (templateId: string): Promise<any> =>
    api.get(`/templates/${templateId}/params`)
};

export const taskApi = {
  fetchBySecretaryId: (secretaryId: string): Promise<RemoteTask[]> => 
    api.get(`/secretaries/${secretaryId}/tasks`),
    
  create: (data: {
    secretaryId: string;
    templateId: string;
    name: string;
    customParams?: Record<string, any>;
  }): Promise<RemoteTask> => 
    api.post('/tasks', data),
    
  update: (id: string, data: Partial<RemoteTask>): Promise<RemoteTask> => 
    api.put(`/tasks/${id}`, data),
    
  delete: (id: string, secretaryId: string): Promise<void> => 
    api.delete(`/tasks/${id}`, { params: { secretaryId } }),
    
  activate: (id: string, secretaryId: string): Promise<void> => 
    api.post(`/tasks/${id}/activate`, null, { params: { secretaryId } }),
    
  deactivate: (id: string, secretaryId: string): Promise<void> => 
    api.post(`/tasks/${id}/deactivate`, null, { params: { secretaryId } }),
    
  updateCustomParams: (id: string, secretaryId: string, customParams: Record<string, any>): Promise<RemoteTask> =>
    api.put(`/tasks/${id}/customParams`, customParams, { params: { secretaryId } }),
    
  getTemplateParams: (templateId: string): Promise<any> =>
    api.get(`/templates/${templateId}/params`)

};

export const logApi = {
  fetchLogs: (limit = 100, offset = 0): Promise<any[]> => 
    api.get('/logs', { params: { limit, offset } })
};

export default api;