import { createContext, useContext ,useState} from 'react'
import api, { tokenStorage } from '../api/client'

const AuthContext = createContext(null)

export function AuthProvider({ children })
{
  //start with the token saved from a previous visit

  const [token,setToken] = useState(()=> tokenStorage.get())

  async function login(email,password){
    const { data } = await api.post('/api/auth/login',{ email, password })
    tokenStorage.set(data.token)
    setToken(data.token)
  }

  //Backend return No token on register just create account
  async function register(name,email,password)
  {
    const { data } = await api.post('/api/auth/register', { name, email, password })
    return data.message
  }

  function logout()
  {
    tokenStorage.clear()
    setToken(null)
  }

  const value = { token, isLoggedIn: !!token, login, register, logout }

  return <AuthContext value={value}>{children}</AuthContext>
}

export function useAuth()
{
   const context = useContext(AuthContext)
   if(!context){
       throw new Error('useAuth must be used inside <AuthProvider>')
   }
   return context
}