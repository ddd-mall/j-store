import { Link, Route, Routes } from 'react-router';
import styles from './App.module.css';

export function App() {
  return (
    <main className={styles.entry}>
      <Routes>
        <Route path="/" element={<h1>J-Store Admin</h1>} />
        <Route
          path="*"
          element={
            <>
              <h1>页面不存在</h1>
              <Link to="/">返回首页</Link>
            </>
          }
        />
      </Routes>
    </main>
  );
}
