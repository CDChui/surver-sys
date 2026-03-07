import { request } from '../utils/request'

export function testApi() {
  return request.get('/test')
}