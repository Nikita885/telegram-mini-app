# backend/api/ai_tagging.py
import torch
import clip
from PIL import Image
import numpy as np

class ClothingTagger:
    def __init__(self):
        self.device = "cuda" if torch.cuda.is_available() else "cpu"
        self.model, self.preprocess = clip.load("ViT-B/32", device=self.device)
        
        # Список возможных тегов
        self.color_labels = ["black", "white", "red", "blue", "green", "yellow", "pink", "gray"]
        self.style_labels = ["casual", "formal", "sport", "streetwear", "vintage"]
        self.material_labels = ["cotton", "denim", "leather", "silk", "wool", "synthetic"]
    
    def tag_image(self, image_path):
        """Определить теги для изображения одежды"""
        image = Image.open(image_path)
        image_input = self.preprocess(image).unsqueeze(0).to(self.device)
        
        tags = []
        
        # Определяем цвет
        color_prompts = [f"a {color} clothing item" for color in self.color_labels]
        color_text = clip.tokenize(color_prompts).to(self.device)
        
        with torch.no_grad():
            image_features = self.model.encode_image(image_input)
            text_features = self.model.encode_text(color_text)
            
            similarity = (100.0 * image_features @ text_features.T).softmax(dim=-1)
            color_idx = similarity[0].argmax().item()
            
            if similarity[0][color_idx] > 0.3:
                tags.append(('color', self.color_labels[color_idx]))
        
        # Определяем стиль
        style_prompts = [f"{style} style clothing" for style in self.style_labels]
        style_text = clip.tokenize(style_prompts).to(self.device)
        
        with torch.no_grad():
            text_features = self.model.encode_text(style_text)
            similarity = (100.0 * image_features @ text_features.T).softmax(dim=-1)
            style_idx = similarity[0].argmax().item()
            
            if similarity[0][style_idx] > 0.3:
                tags.append(('style', self.style_labels[style_idx]))
        
        return tags

# Использование
tagger = ClothingTagger()
tags = tagger.tag_image('/path/to/clothing/image.jpg')
# tags = [('color', 'black'), ('style', 'formal')]