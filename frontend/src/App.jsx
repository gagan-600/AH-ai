import React, { useState } from "react";
import UploadForm from "./components/UploadForm";
import ResultViewer from "./components/ResultViewer";

function App() {
  const [result, setResult] = useState(null);

  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center px-6 py-12">
      <div className="w-full max-w-3xl">
        <header className="mb-6 text-center">
          <h1 className="text-2xl md:text-3xl font-extrabold text-gray-900">
            AI Handwritten Form Reader
          </h1>
          <p className="mt-2 text-sm text-gray-600">
            Upload a scanned form and get structured extracted data.
          </p>
        </header>

        <main className="space-y-6">
          <UploadForm setResult={setResult} />
          {result && <ResultViewer result={result} />}
        </main>

        <footer className="mt-8 text-center text-xs text-gray-400">
          Built by Gagan 
        </footer>
      </div>
    </div>
  );
}

export default App;
