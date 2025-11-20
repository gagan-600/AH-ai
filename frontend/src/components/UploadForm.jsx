import React, { useRef, useState } from "react";
import axios from "axios";

const UploadForm = ({ setResult }) => {
  const inputRef = useRef(null);
  const [fileName, setFileName] = useState("");
  const [loading, setLoading] = useState(false);
  const [progress, setProgress] = useState(0);

  const onFileSelected = async (e) => {
    const file = e.target.files && e.target.files[0];
    if (!file) return;
    setFileName(file.name);
    setLoading(true);
    setProgress(0);
    const formData = new FormData();
    formData.append("file", file);

    try {
      const res = await axios.post("http://localhost:5000/api/upload", formData, {
        headers: { "Content-Type": "multipart/form-data" },
        onUploadProgress: (progressEvent) => {
          const percentCompleted = Math.round(
            (progressEvent.loaded * 100) / progressEvent.total
          );
          setProgress(percentCompleted);
        },
      });
      setResult(typeof res.data === "string" ? JSON.parse(res.data) : res.data);
      setProgress(100);
    } catch (err) {
      console.error(err);
      alert("Upload failed: " + (err.message || err));
      setProgress(0);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="bg-white p-6 rounded-xl shadow-md flex flex-col items-center gap-4">
      <input
        ref={inputRef}
        type="file"
        accept="image/*,application/pdf"
        onChange={onFileSelected}
        className="hidden"
      />

      <button
        onClick={() => inputRef.current && inputRef.current.click()}
        disabled={loading}
        className="w-full max-w-xs bg-gradient-to-r from-blue-600 to-indigo-600 text-white px-6 py-3 rounded-lg font-medium shadow hover:from-blue-700 hover:to-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed"
      >
        {loading ? "Processing…" : "Select & Upload"}
      </button>

      {loading && (
        <div className="w-full max-w-xs">
          <div className="flex justify-between items-center mb-2">
            <span className="text-sm font-medium text-gray-700">Uploading...</span>
            <span className="text-sm font-medium text-gray-700">{progress}%</span>
          </div>
          <div className="w-full bg-gray-200 rounded-full h-2 overflow-hidden">
            <div
              className="bg-gradient-to-r from-blue-600 to-indigo-600 h-full transition-all duration-300 ease-out"
              style={{ width: `${progress}%` }}
            ></div>
          </div>
        </div>
      )}

      <div className="text-sm text-gray-500">{fileName || "No file selected"}</div>
    </div>
  );
};

export default UploadForm;
