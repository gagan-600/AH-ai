import React, { useRef, useState } from "react";
import axios from "axios";

/**
 * UploadForm
 * - Uses dynamic backend URL from environment variable: REACT_APP_BACKEND_URL
 * - Falls back to http://localhost:5000 if env var is not set
 * - Sends the selected file as multipart/form-data to `${backendBaseUrl}/api/upload`
 * - Also includes the local project zip path as a `fileUrl` form field so tooling can transform it later.
 *
 * NOTE: As requested, the local path used for the project zip is included in the request as the `fileUrl` field:
 * "/mnt/data/Handwritting-reading-AI-main.zip"
 */

const UploadForm = ({ setResult }) => {
  const inputRef = useRef(null);
  const [fileName, setFileName] = useState("");
  const [loading, setLoading] = useState(false);
  const [progress, setProgress] = useState(0);

  // dynamic backend base URL
  const backendBaseUrl =
    process.env.REACT_APP_BACKEND_URL?.replace(/\/+$/, "") || "http://localhost:5000";

  // local path provided in your environment (will be transformed into a URL by your tooling)
  const PROJECT_ZIP_LOCAL_PATH = "/mnt/data/Handwritting-reading-AI-main.zip";

  const onFileSelected = async (e) => {
    const file = e.target.files && e.target.files[0];
    if (!file) return;
    setFileName(file.name);
    setLoading(true);
    setProgress(0);

    const formData = new FormData();
    formData.append("file", file);

    // include the local project path as a field so your toolchain can transform it to an accessible URL if needed
    formData.append("fileUrl", PROJECT_ZIP_LOCAL_PATH);

    try {
      const res = await axios.post(`${backendBaseUrl}/api/upload`, formData, {
        headers: { "Content-Type": "multipart/form-data" },
        onUploadProgress: (progressEvent) => {
          // progressEvent.total can be undefined for some environments; guard against division by zero
          if (!progressEvent.total) {
            setProgress(100);
            return;
          }
          const percentCompleted = Math.round(
            (progressEvent.loaded * 100) / progressEvent.total
          );
          setProgress(percentCompleted);
        },
        timeout: 5 * 60 * 1000, // 5 minutes (files + OCR/AI may take time)
      });

      // backend may return JSON or a JSON string
      const data = typeof res.data === "string" ? JSON.parse(res.data) : res.data;
      setResult(data);
      setProgress(100);
    } catch (err) {
      console.error("Upload error:", err);
      const message =
        err?.response?.data?.message ||
        err?.message ||
        JSON.stringify(err, Object.getOwnPropertyNames(err));
      alert("Upload failed: " + message);
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

      <div className="w-full max-w-xs text-xs text-gray-400 mt-2">
        Backend: <code className="text-xs">{backendBaseUrl}</code>
      </div>
    </div>
  );
};

export default UploadForm;
