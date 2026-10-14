import { Routes, Route, Navigate } from 'react-router'
import LoginPage from './pages/LoginPage.jsx'
import RegisterPage from './pages/RegisterPage.jsx'
import FilesPage from './pages/FilesPage.jsx'
import ProtectedLayout from './components/ProtectedLayout.jsx'
import GenerateReportPage from './pages/GenerateReportPage.jsx'
import ReportViewPage from './pages/ReportViewPage.jsx'
import './App.css'

function App() {

   return (
     <Routes>
       <Route path="/" element={<Navigate to="/files" replace />} />
       <Route path="/login" element={<LoginPage />} />
       <Route path="/register" element={<RegisterPage />} />
       <Route element={<ProtectedLayout />}>
         <Route path="/files" element={<FilesPage />} />
         <Route path="/files/:fileId/report" element={<GenerateReportPage />} />
         <Route path="/reports/:reportId" element={<ReportViewPage />} />
       </Route>
       <Route path="*" element={<h1>404 - Page not found </h1>} />
     </Routes>
   )

}

export default App
