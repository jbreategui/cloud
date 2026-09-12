export default function Icon({ name, className = '', fill = false }) {
  return (
    <span
      className={`material-symbols-rounded select-none ${fill ? 'fill' : ''} ${className}`}
      aria-hidden="true"
    >
      {name}
    </span>
  );
}
