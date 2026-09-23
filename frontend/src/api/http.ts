import axios from 'axios';
import { useAuth } from '../store/auth';

const http = axios.create({ baseURL: '/api', timeout: 30000 });

http.interceptors.request.use((cfg) => {
  const token = useAuth.getState().token;
  if (token) {
    cfg.headers.Authorization = `Bearer ${token}`;
  }
  return cfg;
});

function forceLogout() {
  useAuth.getState().logout();
  if (!location.pathname.startsWith('/login')) {
    location.href = '/login';
  }
}

http.interceptors.response.use(
  (resp) => {
    const body = resp.data;
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) {
        return body.data;
      }
      if (body.code === 401) {
        forceLogout();
      }
      return Promise.reject(new Error(body.message || '请求失败'));
    }
    return body;
  },
  (err) => {
    if (err.response?.status === 401) {
      forceLogout();
      return Promise.reject(new Error('登录已过期，请重新登录'));
    }
    const msg = err.response?.data?.message || err.message || '网络错误';
    return Promise.reject(new Error(msg));
  }
);

// 拦截器已把 {code,message,data} 解包为 data，这里统一按 T 返回
export const get = <T = any,>(url: string, params?: any): Promise<T> =>
  http.get(url, { params }) as unknown as Promise<T>;
export const post = <T = any,>(url: string, data?: any): Promise<T> =>
  http.post(url, data) as unknown as Promise<T>;
export const put = <T = any,>(url: string, data?: any): Promise<T> =>
  http.put(url, data) as unknown as Promise<T>;
export const del = <T = any,>(url: string): Promise<T> => http.delete(url) as unknown as Promise<T>;

export default http;
