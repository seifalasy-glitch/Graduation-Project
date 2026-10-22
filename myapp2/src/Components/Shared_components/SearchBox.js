import "./SearchBox.css";

function SearchBox({ value, onChange, placeholder = "Search..." }) {
  return (
    <div className="search-box">
      <input
        className="search-input"
        type="text"
        placeholder={placeholder}
        value={value}
        onChange={(e) => onChange(e.target.value)}
      />
    </div>
  );
}

export default SearchBox;
