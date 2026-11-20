'use strict';
/**
 * Garante o latest.yml da atualização automática (electron-updater) ao lado do instalador.
 * O electron-builder costuma gerá-lo; se não gerou (build com --publish never), cria aqui
 * com o mesmo formato: versão, arquivo, sha512 em base64 e tamanho.
 */
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');

const pasta = path.join(__dirname, '..', 'release-dist');
const versao = require('../package.json').version;
const nome = `SGE-Diario-Desktop-Setup-${versao}.exe`;
const exe = path.join(pasta, nome);
const destino = path.join(pasta, 'latest.yml');

if (!fs.existsSync(exe)) { console.error(`Instalador não encontrado: ${exe}`); process.exit(1); }
if (fs.existsSync(destino) && fs.readFileSync(destino, 'utf8').includes(nome)) { console.log('latest.yml já existe.'); process.exit(0); }
const sha512 = crypto.createHash('sha512').update(fs.readFileSync(exe)).digest('base64');
const tamanho = fs.statSync(exe).size;
fs.writeFileSync(destino, [
  `version: ${versao}`,
  'files:',
  `  - url: ${nome}`,
  `    sha512: ${sha512}`,
  `    size: ${tamanho}`,
  `path: ${nome}`,
  `sha512: ${sha512}`,
  `releaseDate: '${new Date().toISOString()}'`,
  '',
].join('\n'));
console.log(`latest.yml criado para ${nome}`);
