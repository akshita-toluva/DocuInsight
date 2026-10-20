import axios from 'axios'

const TOKEN_KEY='docuinsight_token'


//Read or Write JWT
export const tokenStorage={
    get:() => localStorage.getItem(TOKEN_KEY),
    set:(token) => localStorage.setItem(TOKEN_KEY,token),
    clear:() => localStorage.removeItem(TOKEN_KEY),
}

//Pre-configured client: every call starts with the backend url
const api=axios.create({
   baseURL:import.meta.env.VITE_API_URL,
   timeout:120000, //2 min
})

//request interceptor : attach the jwt to every call
api.interceptors.request.use((config)=>{
  const token=tokenStorage.get()
  if(token){
    config.headers.Authorization=`Bearer ${token}`
  }
  return config
})

//response interceptor : on 401 (expired/invalid token) - log out
api.interceptors.response.use(
  (response) => response,
  (error) => {
      const status = error.response?.status
      const isAuthCall = error.config?.url?.startsWith('/api/auth/')
      if(status === 401 && !isAuthCall)
      {
        tokenStorage.clear()
        window.location.href='/login'
      }
      return Promise.reject(error)
   }
)

//readable error
export function getErrorMessage(error)
{
   if(!error.response) return 'Cannot reach the server. Is the backend running?'

   const data=error.response.data
   if(data?.error) return data.error
   if(data && typeof data === 'object')
   {
     return Object.values(data).join(' ')
   }
   return 'Something went wrong. Please try again.'
}

export default api