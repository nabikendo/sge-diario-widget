'use strict';
/**
 * Ícone da bandeja e do ícone da barra de tarefas com o número de pendências, desenhado pixel a pixel
 * (sem fontes nem imagens externas). Devolve um bitmap BGRA para nativeImage.createFromBitmap.
 */
const DIGITOS = { // fonte 3x5
  0: '111101101101111', 1: '010110010010111', 2: '111001111100111', 3: '111001111001111', 4: '101101111001001',
  5: '111100111001111', 6: '111100111101111', 7: '111001010010010', 8: '111101111101111', 9: '111101111001111', '+': '000010111010000',
};

function gerarBitmap(n, tamanho = 32) {
  const buf = Buffer.alloc(tamanho * tamanho * 4);
  const cor = n > 0 ? [0xe0, 0x57, 0x4f] : [0x6b, 0x54, 0xee]; // vermelho com pendência, lavanda em dia (R,G,B)
  const r = tamanho / 2 - 1;
  const pixel = (x, y, [R, G, B], a = 255) => { const i = (y * tamanho + x) * 4; buf[i] = B; buf[i + 1] = G; buf[i + 2] = R; buf[i + 3] = a; };
  for (let y = 0; y < tamanho; y++) for (let x = 0; x < tamanho; x++) {
    const d = Math.hypot(x + 0.5 - tamanho / 2, y + 0.5 - tamanho / 2);
    if (d <= r) pixel(x, y, cor); else if (d <= r + 1) pixel(x, y, cor, Math.round(255 * (r + 1 - d)));
  }
  const texto = n > 99 ? '99+' : n > 0 ? String(n) : '✓';
  if (texto === '✓') { // visto
    const traco = [[8, 16], [9, 17], [10, 18], [11, 19], [12, 18], [13, 17], [14, 16], [15, 15], [16, 14], [17, 13], [18, 12], [19, 11], [20, 10]];
    for (const [x, y] of traco) for (let dx = 0; dx < 2; dx++) for (let dy = 0; dy < 3; dy++) pixel(Math.round(x * tamanho / 32) + dx, Math.round(y * tamanho / 32) + dy - 1, [255, 255, 255]);
    return buf;
  }
  const escala = texto.length === 1 ? Math.floor(tamanho / 8) : texto.length === 2 ? Math.floor(tamanho / 10) : Math.floor(tamanho / 14);
  const larg = texto.length * 3 * escala + (texto.length - 1) * escala;
  const x0 = Math.floor((tamanho - larg) / 2); const y0 = Math.floor((tamanho - 5 * escala) / 2);
  [...texto].forEach((c, k) => {
    const g = DIGITOS[c];
    for (let gy = 0; gy < 5; gy++) for (let gx = 0; gx < 3; gx++) {
      if (g[gy * 3 + gx] !== '1') continue;
      for (let sy = 0; sy < escala; sy++) for (let sx = 0; sx < escala; sx++) pixel(x0 + k * 4 * escala + gx * escala + sx, y0 + gy * escala + sy, [255, 255, 255]);
    }
  });
  return buf;
}

module.exports = { gerarBitmap };
