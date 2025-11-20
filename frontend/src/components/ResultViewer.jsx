import React, { useState } from "react";

const ResultViewer = ({ result }) => {
  const [viewMode, setViewMode] = useState("formatted"); // "formatted" or "json"

  // Helper: desired display fields (label and canonical key)
  const displayFields = [
    { label: "Name", key: "name" },
    { label: "Phone", key: "phone" },
    { label: "Email", key: "email" },
    { label: "Address", key: "address" },
    { label: "Postcode", key: "postcode" },
    { label: "Date of Birth", key: "dob" },
  ];

  // If backend returned pages/fields schema, flatten into a map for easy lookup
  const fieldMap = {};
  if (result && result.pages && Array.isArray(result.pages) && result.pages.length > 0) {
    const farr = result.pages[0].fields || [];
    farr.forEach((f) => {
      if (f && f.name) fieldMap[f.name] = f;
    });
  }

  // Remove confidence from result for display
  const filterResult = (obj) => {
    if (Array.isArray(obj)) {
      return obj.map(filterResult);
    }
    
    if (obj !== null && typeof obj === "object") {
      return Object.keys(obj).reduce((acc, key) => {
        if (key !== "confidence") {
          acc[key] = filterResult(obj[key]);
        }
        return acc;
      }, {});
    }
    return obj;
  };

  // Helper to show a field value
  const renderValue = (key) => {
    const entry = fieldMap[key];
    if (!entry || !entry.value) {
      return null; // Return null to hide this field
    }
    return <div className="whitespace-pre-line max-w-lg break-words font-medium text-gray-900">{entry.value}</div>;
  };

  // Get only fields that have values
  const getAvailableFields = () => {
    return displayFields.filter(({ key }) => {
      const entry = fieldMap[key];
      return entry && entry.value;
    });
  };

  // Download JSON file
  const downloadJSON = () => {
    const dataStr = JSON.stringify(filterResult(result), null, 2);
    const dataBlob = new Blob([dataStr], { type: "application/json" });
    const url = URL.createObjectURL(dataBlob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `extracted-data-${new Date().getTime()}.json`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  };

  return (
    <div className="mt-8 bg-gradient-to-br from-white to-gray-50 rounded-2xl shadow-lg overflow-hidden">
      {/* Header */}
      <div className="bg-gradient-to-r from-blue-600 to-indigo-600 px-8 py-6">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-2xl font-bold text-white">Extracted Data</h2>
            <p className="text-blue-100 text-sm mt-1">Official OCR Recognition Results</p>
          </div>
          <div className="text-4xl">📄</div>
        </div>
      </div>

      {/* View Mode Toggle & Actions */}
      <div className="bg-white border-b border-gray-200 px-8 py-4 flex items-center justify-between gap-4">
        <div className="flex gap-2">
          <button
            onClick={() => setViewMode("formatted")}
            className={`px-6 py-2 rounded-lg font-medium transition-all ${
              viewMode === "formatted"
                ? "bg-blue-600 text-white shadow-md"
                : "bg-gray-100 text-gray-700 hover:bg-gray-200"
            }`}
          >
            📋 Formatted View
          </button>
          <button
            onClick={() => setViewMode("json")}
            className={`px-6 py-2 rounded-lg font-medium transition-all ${
              viewMode === "json"
                ? "bg-blue-600 text-white shadow-md"
                : "bg-gray-100 text-gray-700 hover:bg-gray-200"
            }`}
          >
            {} JSON View
          </button>
        </div>
        <button
          onClick={downloadJSON}
          className="flex items-center gap-2 px-6 py-2 bg-green-600 text-white rounded-lg font-medium hover:bg-green-700 shadow-md transition-all"
        >
          ⬇️ Download JSON
        </button>
      </div>

      {/* Content */}
      <div className="px-8 py-8">
        {viewMode === "formatted" ? (
          // Formatted Table View - Only show fields with values
          <div className="overflow-x-auto">
            {getAvailableFields().length > 0 ? (
              <table className="w-full border-collapse">
                <tbody>
                  {getAvailableFields().map(({ label, key }) => (
                    <tr
                      key={key}
                      className="border-b border-gray-200 hover:bg-blue-50 transition-colors"
                    >
                      <td className="font-semibold text-gray-700 px-4 py-4 bg-gray-50 w-40">
                        {label}
                      </td>
                      <td className="px-4 py-4 text-gray-800 text-base">
                        {renderValue(key)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : (
              <div className="text-center py-8 text-gray-500">
                <p className="text-lg">No data fields found in the document</p>
              </div>
            )}
          </div>
        ) : (
          // JSON View
          <div className="bg-gray-900 text-gray-100 rounded-xl p-6 overflow-x-auto font-mono text-sm">
            <pre style={{ whiteSpace: "pre-wrap", wordWrap: "break-word" }}>
              {result ? JSON.stringify(filterResult(result), null, 2) : ""}
            </pre>
          </div>
        )}
      </div>

      {/* Footer */}
      <div className="bg-gray-50 border-t border-gray-200 px-8 py-4">
        <p className="text-xs text-gray-500 flex items-center gap-2">
          • Generated at {new Date().toLocaleString()}
        </p>
      </div>
    </div>
  );
};

export default ResultViewer;
